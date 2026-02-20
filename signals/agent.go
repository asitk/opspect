package signals

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"net"
	"os"
	"sync"
	"time"

	log "github.com/sirupsen/logrus"

	"strings"

	"opspect/config"
	"opspect/models/client"
	"opspect/signals/inputs/plugins"
	"opspect/signals/inputs/plugins/kafka"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata"
	"opspect/signals/outputs"
)

// Agent collects data based on the given config
type Agent struct {
	Config *config.Config
}

var tagChanged = make(chan bool)
var tags string = ""
var kafkaReadNotify = make(chan bool)

func getNodeDetails(a *Agent) {
	kafka.SubscribeForNotification(&kafkaReadNotify)
	for <-kafkaReadNotify {
		message := kafka.Read()
		if len(message) > 0 {
			//fmt.Printf("Agent got message from kafka %s\n", message)
			_, err := nwam.GetNWTagsObj(message)
			if err == nil {
				tags = message
				tagChanged <- true
			}
		}
	}
	return
}

func updateConfig(a *Agent) error {
	hostip := strings.Split(getHostID(), ":")[1]
	a.Config.Tags["host"] = getHostID()
	//Add a function to get the category associated with this hostID and tenant or customerID
	o, err := nwam.GetNWTagsObj(tags)
	if err == nil && len(o.NodeDetails) > 0 {
		obj := o.NodeDetails[hostip]
		if len(obj.ClusterID) > 0 {
			a.Config.Tags["cluster_id"] = obj.ClusterID
			a.Config.Tags["deployment_id"] = obj.DeploymentID
			a.Config.Tags["customer_id"] = obj.CustID
			fmt.Printf("Updating Agent Config Now.....\n")
			return nil
		}
	}
	return err
}

func GetStarted(a *Agent) {
	for <-tagChanged {
		updateConfig(a)
	}
}

// NewAgent returns an Agent struct based off the given Config
func NewAgent(config *config.Config) (*Agent, error) {
	a := &Agent{
		Config: config,
	}

	if a.Config.Agent.Hostname == "" {
		hostname, err := os.Hostname()
		if err != nil {
			return nil, err
		}

		a.Config.Agent.Hostname = hostname
	}

	go GetStarted(a)
	go getNodeDetails(a)
	// We are adding a hostname as a prefix
	// config.Tags["host"] = a.Config.Agent.Hostname
	config.Tags["host"] = getHostID()
	//Add a function to get the category associated with this hostID and tenant or customerID
	config.Tags["cluster_id"] = "unknown"
	config.Tags["deployment_id"] = "unknown"
	config.Tags["customer_id"] = "unknown"
	return a, nil
}

// Connect connects to all configured outputs
func (a *Agent) Connect() error {
	for _, o := range a.Config.Outputs {
		switch ot := o.Output.(type) {
		case outputs.ServiceOutput:
			if err := ot.Start(); err != nil {
				log.Printf("Service for output %s failed to start, exiting\n%s\n",
					o.Name, err.Error())
				return err
			}
		}

		if a.Config.Agent.Debug {
			log.Printf("Attempting connection to output: %s\n", o.Name)
		}
		err := o.Output.Connect()
		if err != nil {
			log.Printf("Failed to connect to output %s, retrying in 15s\n", o.Name)
			time.Sleep(15 * time.Second)
			err = o.Output.Connect()
			if err != nil {
				return err
			}
		}
		if a.Config.Agent.Debug {
			log.Printf("Successfully connected to output: %s\n", o.Name)
		}
	}
	return nil
}

// Close closes the connection to all configured outputs
func (a *Agent) Close() error {
	var err error
	for _, o := range a.Config.Outputs {
		err = o.Output.Close()
		switch ot := o.Output.(type) {
		case outputs.ServiceOutput:
			ot.Stop()
		}
	}
	return err
}

func getHostID() string {

	hostID, err := os.Hostname()
	if err != nil {
		log.Printf("Error in retrieving hostname: %s", err)
		panic(1)
	}

	ifaces, err := net.Interfaces()
	if err != nil {
		log.Printf("Error in retrieving interfaces: %s", err)
		panic(1)
	}

	var ip net.IP
	for _, i := range ifaces {
		addrs, _ := i.Addrs()

		for _, addr := range addrs {
			if ipnet, noerr := addr.(*net.IPNet); noerr && !ipnet.IP.IsLoopback() {
				switch v := addr.(type) {
				case *net.IPNet:
					ip = v.IP
					if ip.To4() != nil {
						hostID += ":" + ip.String()
					}
				}
			}
		}
	}

	return hostID
}

// gatherParallel runs the plugins that are using the same reporting interval
// as the agent
func (a *Agent) gatherParallel(pointChan chan *client.Point) error {
	var wg sync.WaitGroup

	start := time.Now()
	counter := 0

	//hostprefix := getHostID() + "."
	hostprefix := ""

	for _, plugin := range a.Config.Plugins {
		if plugin.Config.Interval != 0 {
			continue
		}

		// asit: Todo: Set explicit flag for all plugin configs
		// Do not handle self managed plugins
		if plugin.Config.Unmanaged {
			log.Printf("gatherParallel: Skipping unmanaged plugin = %s", plugin.Name)
			continue
		}

		wg.Add(1)
		counter++
		go func(plugin *config.RunningPlugin) {
			defer wg.Done()

			acc := NewAccumulator(plugin.Config, pointChan)
			acc.SetDebug(a.Config.Agent.Debug)
			acc.SetPrefix(hostprefix + "plugin=" + plugin.Name + ".")
			acc.SetDefaultTags(a.Config.Tags)

			if err := plugin.Plugin.Gather(acc); err != nil {
				log.Printf("Error in plugin [%s]: %s", plugin.Name, err)
			}
		}(plugin)
	}

	if counter == 0 {
		return nil
	}

	wg.Wait()

	elapsed := time.Since(start)
	log.Printf("Gathered metrics, (%s interval), from %d plugins in %s",
		a.Config.Agent.Interval, counter, elapsed)
	return nil
}

// gatherSeparate runs the plugins that have been configured with their own
// reporting interval.
func (a *Agent) gatherSeparate(
	shutdown chan struct{},
	plugin *config.RunningPlugin,
	pointChan chan *client.Point,
) error {
	ticker := time.NewTicker(plugin.Config.Interval)

	//hostprefix := getHostID() + "."
	hostprefix := ""

	for {
		var outerr error
		start := time.Now()

		acc := NewAccumulator(plugin.Config, pointChan)
		acc.SetDebug(a.Config.Agent.Debug)
		acc.SetPrefix(hostprefix + "plugin=" + plugin.Name + ".")
		acc.SetDefaultTags(a.Config.Tags)

		if err := plugin.Plugin.Gather(acc); err != nil {
			log.Printf("Error in plugin [%s]: %s", plugin.Name, err)
		}

		elapsed := time.Since(start)
		log.Printf("Gathered metrics, (separate %s interval), from %s in %s",
			plugin.Config.Interval, plugin.Name, elapsed)

		if outerr != nil {
			return outerr
		}

		select {
		case <-shutdown:
			return nil
		case <-ticker.C:
			continue
		}
	}
}

// gatherUnmanaged ...
// Keeps running plugins in parallel. Plugins are responsible for writing
// output directly
func (a *Agent) gatherUnmanaged(
	shutdown chan struct{},
	plugin *config.RunningPlugin,
	pointChan chan *client.Point,
) error {

	//hostprefix := getHostID() + "."
	hostprefix := ""

	for {

		var outerr error
		// asit : todo Interval is not supported for unmanaged plugins
		if plugin.Config.Interval != 0 {
			log.Printf("gatherUnmanaged: Skipping plugin = %s with Interval", plugin.Name)
			continue
		}

		// asit: Todo: Set explicit flag for all plugin configs
		// Do not handle self managed plugins
		if !plugin.Config.Unmanaged {
			log.Printf("gatherUnmanaged: Skipping unmanaged plugin = %s", plugin.Name)
			continue
		}

		log.Printf("Debug: Running Unmanaged Plugin: %s", plugin.Name)

		acc := NewAccumulator(plugin.Config, pointChan)
		acc.SetDebug(a.Config.Agent.Debug)
		acc.SetPrefix(hostprefix + "plugin=" + plugin.Name + ".")
		acc.SetDefaultTags(a.Config.Tags)

		if err := plugin.Plugin.Gather(acc); err != nil {
			log.Printf("Error in plugin [%s]: %s", plugin.Name, err)
		}
		if outerr != nil {
			return outerr
		}

		select {
		case <-shutdown:
			return nil
		}
	}
}

// Test verifies that we can 'Gather' from all plugins with their configured
// Config struct
func (a *Agent) Test() error {
	shutdown := make(chan struct{})
	defer close(shutdown)
	pointChan := make(chan *client.Point)

	// hostprefix := getHostID() + "."
	hostprefix := ""

	// dummy receiver for the point channel
	go func() {
		for {
			select {
			case <-pointChan:
				// do nothing
			case <-shutdown:
				return
			}
		}
	}()

	for _, plugin := range a.Config.Plugins {
		acc := NewAccumulator(plugin.Config, pointChan)

		acc.SetDebug(true)
		acc.SetPrefix(hostprefix + "plugin=" + plugin.Name + ".")

		log.Printf("* Plugin: %s, Collection 1\n", plugin.Name)
		if plugin.Config.Interval != 0 {
			log.Printf("* Interval: %s\n", plugin.Config.Interval)
		}

		// asit: Todo: Set explicit flag for all plugin configs
		// Do not handle self managed plugins
		if plugin.Config.Unmanaged {
			log.Printf("DiskUsagebug: Skipping unmanaged plugin = %s", plugin.Name)
			continue
		}

		if err := plugin.Plugin.Gather(acc); err != nil {
			return err
		}

		// Special instructions for some plugins. cpu, for example, needs to be
		// run twice in order to return cpu usage percentages.
		switch plugin.Name {
		case "cpu", "mongodb":
			time.Sleep(500 * time.Millisecond)
			log.Printf("* Plugin: %s, Collection 2\n", plugin.Name)
			if err := plugin.Plugin.Gather(acc); err != nil {
				return err
			}
		}

	}
	return nil
}

// writeOutput writes a list of points to a single output, with retries.
// Optionally takes a `done` channel to indicate that it is done writing.
func (a *Agent) writeOutput(
	points []*client.Point,
	ro *config.RunningOutput,
	shutdown chan struct{},
	wg *sync.WaitGroup,
) {
	defer wg.Done()
	if len(points) == 0 {
		return
	}
	retry := 0
	retries := a.Config.Agent.FlushRetries
	start := time.Now()

	log.Printf("Flushing at %s\n", start.Format(time.UnixDate))

	for {
		filtered := ro.FilterPoints(points)
		err := ro.Output.Write(filtered)
		if err == nil {
			// Write successful
			elapsed := time.Since(start)
			log.Printf("Flushed %d metrics to output %s in %s\n",
				len(filtered), ro.Name, elapsed)
			return
		}

		select {
		case <-shutdown:
			return
		default:
			if retry >= retries {
				// No more retries
				msg := "FATAL: Write to output [%s] failed %d times, dropping" +
					" %d metrics\n"
				log.Printf(msg, ro.Name, retries+1, len(points))
				return
			} else if err != nil {
				// Sleep for a retry
				log.Printf("Error in output [%s]: %s, retrying in %s",
					ro.Name, err.Error(), a.Config.Agent.FlushInterval.Duration)
				time.Sleep(a.Config.Agent.FlushInterval.Duration)
			}
		}

		retry++
	}
}

// flush writes a list of points to all configured outputs
func (a *Agent) flush(
	points []*client.Point,
	shutdown chan struct{},
	wait bool,
) {
	var wg sync.WaitGroup
	for _, o := range a.Config.Outputs {
		wg.Add(1)
		go a.writeOutput(points, o, shutdown, &wg)
	}
	if wait {
		wg.Wait()
	}
}

// flusher monitors the points input channel and flushes on the minimum interval
func (a *Agent) flusher(ForceFlushChan chan bool, shutdown chan struct{}, pointChan chan *client.Point) error {
	// Inelegant, but this sleep is to allow the Gather threads to run, so that
	// the flusher will flush after metrics are collected.
	time.Sleep(time.Millisecond * 100)

	ticker := time.NewTicker(a.Config.Agent.FlushInterval.Duration)
	points := make([]*client.Point, 0)

	for {
		select {
		case <-ForceFlushChan:
			log.Println("Forced flush of cached points")
			a.flush(points, shutdown, false)
			points = make([]*client.Point, 0)

		case <-shutdown:
			log.Println("Hang on, flushing any cached points before shutdown")
			a.flush(points, shutdown, true)
			a.Close()
			return nil
		case <-ticker.C:
			a.flush(points, shutdown, false)
			points = make([]*client.Point, 0)
		case pt := <-pointChan:
			points = append(points, pt)
		}
	}
}

// jitterInterval applies the the interval jitter to the flush interval using
// crypto/rand number generator
func jitterInterval(ininterval, injitter time.Duration) time.Duration {
	var jitter int64
	outinterval := ininterval
	if injitter.Nanoseconds() != 0 {
		maxjitter := big.NewInt(injitter.Nanoseconds())
		if j, err := rand.Int(rand.Reader, maxjitter); err == nil {
			jitter = j.Int64()
		}
		outinterval = time.Duration(jitter + ininterval.Nanoseconds())
	}

	if outinterval.Nanoseconds() < time.Duration(2000*time.Millisecond).Nanoseconds() {
		log.Printf("Flush interval %s too low, setting to 2000ms\n", outinterval)
		outinterval = time.Duration(2000 * time.Millisecond)
	}

	return outinterval
}

// Run runs the agent daemon, gathering every Interval
func (a *Agent) Run(shutdown chan struct{}) error {
	var wg sync.WaitGroup

	ForceFlushChan := make(chan bool)
	var IsForceFlushEnabled = false
	if a.Config.Agent.ForceFlush == "true" {
		IsForceFlushEnabled = true
	}

	a.Config.Agent.FlushInterval.Duration = jitterInterval(a.Config.Agent.FlushInterval.Duration,
		a.Config.Agent.FlushJitter.Duration)

	log.Printf("Agent Config: Interval:%s, Debug:%#v, Hostname:%#v, "+
		"Flush Interval:%s",
		a.Config.Agent.Interval, a.Config.Agent.Debug,
		a.Config.Agent.Hostname, a.Config.Agent.FlushInterval)

	// buffered channel shared between all plugin threads for accumulating points
	pointChan := make(chan *client.Point, 100000)

	// Round collection to nearest interval by sleeping
	if a.Config.Agent.RoundInterval {
		i := int64(a.Config.Agent.Interval.Duration)
		time.Sleep(time.Duration(i - (time.Now().UnixNano() % i)))
	}
	ticker := time.NewTicker(a.Config.Agent.Interval.Duration)

	// buffered channel shared between all unmanaged plugin threads for accumulating
	// points. asit: todo : fix buffer size at both channel based on available memory
	// pointChanUnmanaged := make(chan *client.Point, 1000000)

	// fork off non interval based flusher for unmanaged plugins
	// with seperate point channel : todo: fix interval
	// wg.Add(1)
	// go func() {
	//defer wg.Done()
	//if err := a.flusher(shutdown, pointChanUnmanaged); err != nil {
	//log.Printf("Flusher routine failed, exiting: %s\n", err.Error())
	//close(shutdown)
	//}
	//}()

	// fork off flusher for common point channel
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := a.flusher(ForceFlushChan, shutdown, pointChan); err != nil {
			log.Printf("Flusher routine failed, exiting: %s\n", err.Error())
			close(shutdown)
		}
	}()

	// asit debug
	// for _, plugin := range a.Config.Plugins {
	// log.Printf("Plugin %v %v", plugin.Config.Name, plugin.Config.Unmanaged)
	//}

	// Handle Interval based and Unmanaged Plugins
	for _, plugin := range a.Config.Plugins {

		// Start service of any ServicePlugins
		switch p := plugin.Plugin.(type) {
		case plugins.ServicePlugin:
			if err := p.Start(); err != nil {
				log.Printf("Service for plugin %s failed to start, exiting\n%s\n",
					plugin.Name, err.Error())
				return err
			}
			defer p.Stop()
		}

		// Special handling for plugins that have their own collection interval
		// configured. Default intervals are handled below with gatherParallel
		if plugin.Config.Interval != 0 {
			wg.Add(1)
			go func(plugin *config.RunningPlugin) {
				defer wg.Done()
				if err := a.gatherSeparate(shutdown, plugin, pointChan); err != nil {
					log.Print(err)
				}
			}(plugin)
		}

		if plugin.Config.Unmanaged {
			wg.Add(1)

			go func(plugin *config.RunningPlugin) {
				defer wg.Done()
				//if err := a.gatherUnmanaged(shutdown, plugin, pointChanUnmanaged); err != nil {
				if err := a.gatherUnmanaged(shutdown, plugin, pointChan); err != nil {
					log.Print(err)
				}
			}(plugin)
		}
	}

	defer wg.Wait()

	for {
		startts := time.Now()

		log.Printf("Plugins Collection, (%s interval), %s", a.Config.Agent.Interval, startts.Format(time.UnixDate))

		if err := a.gatherParallel(pointChan); err != nil {
			log.Print(err)
		}

		elapsedts := time.Since(startts)

		log.Printf("Done, (%s interval), elapsed %s", a.Config.Agent.Interval, elapsedts)

		if IsForceFlushEnabled {
			// Flush points aggressively ...
			ForceFlushChan <- true
		}

		select {
		case <-shutdown:
			return nil
		case <-ticker.C:
			continue
		}
	}
}

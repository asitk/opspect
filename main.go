package main

import (
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"

	log "github.com/sirupsen/logrus"

	"opspect/config"
	"opspect/signals"
	_ "opspect/signals/inputs/plugins/all"
	_ "opspect/signals/outputs/all"
)

var fDebug = flag.Bool("debug", false,
	"show metrics as they're generated to stdout")
var fTest = flag.Bool("test", false, "gather metrics, print them out, and exit")
var fConfig = flag.String("config", "", "configuration file to load")
var fConfigDirectory = flag.String("configdirectory", "",
	"directory containing additional *.conf files")
var fVersion = flag.Bool("version", false, "display the version")
var fSampleConfig = flag.Bool("sample-config", false,
	"print out full sample configuration")
var fPidfile = flag.String("pidfile", "", "file to write our pid to")
var fPLuginFilters = flag.String("filter", "",
	"filter the plugins to enable, separator is :")
var fOutputFilters = flag.String("outputfilter", "",
	"filter the outputs to enable, separator is :")
var fUsage = flag.String("usage", "",
	"print usage for a plugin, ie, 'main -usage mysql'")

//var fDiscover = flag.Bool("discover", false, "print autodiscovered services to stdout")

var Version string

func main() {
	flag.Parse()

	var pluginFilters []string
	if *fPLuginFilters != "" {
		pluginsFilter := strings.TrimSpace(*fPLuginFilters)
		pluginFilters = strings.Split(":"+pluginsFilter+":", ":")
	}

	var outputFilters []string
	if *fOutputFilters != "" {
		outputFilter := strings.TrimSpace(*fOutputFilters)
		outputFilters = strings.Split(":"+outputFilter+":", ":")
	}

	Version = "1.0"
	if *fVersion {
		v := fmt.Sprintf("Agent - Version %s", Version)
		fmt.Println(v)
		return
	}

	/*
		if *fDiscover {
			discovery.GetProcessList()
			return
		}
	*/

	if *fSampleConfig {
		config.PrintSampleConfig(pluginFilters, outputFilters)
		return
	}

	if *fUsage != "" {
		if err := config.PrintPluginConfig(*fUsage); err != nil {
			if err2 := config.PrintOutputConfig(*fUsage); err2 != nil {
				log.Fatalf("%s and %s", err, err2)
			}
		}
		return
	}

	var (
		c   *config.Config
		err error
	)

	if *fConfig != "" {
		c = config.NewConfig()
		c.OutputFilters = outputFilters
		c.PluginFilters = pluginFilters
		err = c.LoadConfig(*fConfig)
		if err != nil {
			log.Fatal(err)
		}
	} else {
		fmt.Println("Usage: ")
		flag.PrintDefaults()
		return
	}

	if *fConfigDirectory != "" {
		err = c.LoadDirectory(*fConfigDirectory)
		if err != nil {
			log.Fatal(err)
		}
	}
	if len(c.Outputs) == 0 {
		log.Fatalf("Error: no outputs found, did you provide a valid config file?")
	}
	if len(c.Plugins) == 0 {
		log.Fatalf("Error: no plugins found, did you provide a valid config file?")
	}

	ag, err := signals.NewAgent(c)
	if err != nil {
		log.Fatal(err)
	}

	if *fDebug {
		ag.Config.Agent.Debug = true
	}

	if *fTest {
		err = ag.Test()
		if err != nil {
			log.Fatal(err)
		}
		return
	}

	err = ag.Connect()
	if err != nil {
		log.Fatal(err)
	}

	shutdown := make(chan struct{})
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-signals
		close(shutdown)
	}()

	log.Printf("Starting Agent (version %s)", Version)
	log.Printf("Loaded outputs: %s", strings.Join(c.OutputNames(), " "))
	log.Printf("Loaded plugins: %s", strings.Join(c.PluginNames(), " "))
	log.Printf("Tags enabled: %s", c.ListTags())

	if *fPidfile != "" {
		f, err := os.Create(*fPidfile)
		if err != nil {
			log.Fatalf("Unable to create pidfile: %s", err)
		}

		fmt.Fprintf(f, "%d\n", os.Getpid())

		f.Close()
	}

	ag.Run(shutdown)
}

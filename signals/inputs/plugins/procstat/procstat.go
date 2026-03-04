package procstat

import (
	"opscog/signals/inputs/plugins"
	"opscog/util/discovery"
)

type Specification struct {
	PidFile  string `toml:"pid_file"`
	Exe      string `toml:"exe"`
	Prefix   string `toml:"prefix"`
	Pattern  string `toml:"pattern"`
	Category int    `toml:"category"`
	Name     string `toml:"name"`
}

type Procstat struct {
	Specifications []Specification
	piList         *[]discovery.ProcInfoSnapshot
}

func NewProcstat() *Procstat {
	return &Procstat{}
}

var sampleConfig = `
  [[plugins.procstat.specifications]]
  prefix = "" # optional string to prefix measurements
  # Must specify one of: pid_file, exe, or pattern
  # PID file to monitor process
  pid_file = "/var/run/nginx.pid"
  # executable name (ie, pgrep <exe>)
  # exe = "nginx"
  # pattern as argument for pgrep (ie, pgrep -f <pattern>)
  # pattern = "nginx"
`

func (_ *Procstat) SampleConfig() string {
	return sampleConfig
}

func (_ *Procstat) Description() string {
	return "Monitor process cpu and memory usage"
}

func (p *Procstat) GatherUnmanagedAsync(shutdown chan struct{}) error {
	return nil
}

// Gather ...
func (p *Procstat) Gather(acc plugins.Accumulator) error {
	//var wg sync.WaitGroup

	GetProcessInfo(acc, p.Specifications)

	//for _, specification := range p.Specifications {
	//fmt.Printf("spec = %s\n", specification.Pattern)

	// wg.Add(1)
	//go func(spec *Specification, acc plugins.Accumulator) {
	//go func(pids []int32, acc plugins.Accumulator) {
	//defer wg.Done()
	//}(pids, acc)
	//}
	//wg.Wait()

	return nil
}

func init() {
	plugins.Add("procstat", func() plugins.Plugin {
		return NewProcstat()
	})
}

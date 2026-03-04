// Package logs ...
package logs

import (
	"sync"

	"github.com/hpcloud/tail"
	log "github.com/sirupsen/logrus"
	"opscog/signals/inputs/plugins"
)

// Logfile ...
type Logfile struct {
	Filename     string `toml:"filename"`
	Pattern      string `toml:"pattern"`
	TimeStartPos int    `toml:"timestartpos"`
	TimeEndPos   int    `toml:"timeendpos"`
}

// LogStats ...
type LogStats struct {
	Logfiles  map[string]Logfile `toml:"logfiles"`
	Unmanaged bool               `toml:"unmanaged"`
}

// NewLogStats ...
func NewLogStats(Filenames []string) *LogStats {
	return &LogStats{}
}

// Description ...
func (l *LogStats) Description() string {
	return "Read metrics about application logs"
}

// sampleConfig ...
var sampleConfig = `
	filename = "/var/log/apache2/error.log"
  pattern = ""
  timestartpos = 2
  timeendpos = 10
`

// SampleConfig ...
func (l *LogStats) SampleConfig() string {
	return sampleConfig
}

// GatherUnmanagedAsync ...
// asit: Todo Create a better unmanaged plugin interface
func (l *LogStats) GatherUnmanagedAsync(shutdown chan struct{}) error {
	return nil
}

// Gather ...
func (l *LogStats) Gather(acc plugins.Accumulator) error {
	var wg sync.WaitGroup

	//log.Println("Inside Unmanaged Gather map:", l.Logfiles)
	for k, val := range l.Logfiles {

		log.Printf("%v, %v", k, val)
		wg.Add(1)
		go func(tag string, filename string, acc plugins.Accumulator) {
			defer wg.Done()

			log.Printf("Starting file %s Tag %s", filename, tag)
			dropc := 0
			t, err := tail.TailFile(filename, tail.Config{Follow: true, ReOpen: true})
			if err != nil {
				log.Panic(err)
			}

			tags := map[string]string{
				"logs": tag,
			}

			for line := range t.Lines {
				// log.Printf("%v", line.Time.String())

				// Drop stale points
				if dropc < 10 {
					dropc++
					continue
				}

				acc.Add("line", line.Text, tags, line.Time)
			}
		}(k, val.Filename, acc)
	}

	return nil
}

func init() {
	plugins.Add("logs", func() plugins.Plugin {
		return &LogStats{}
	})
}

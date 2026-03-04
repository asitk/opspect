// Package signals ...
// This implements the accumulator
package signals

import (
	"math"
	"sync"
	"time"

	log "github.com/sirupsen/logrus"

	"opscog/config"
	"opscog/models/client"
)

// Accumulator ...
type Accumulator interface {
	Add(measurement string, value interface{},
		tags map[string]string, t ...time.Time)
	AddFields(measurement string, fields map[string]interface{},
		tags map[string]string, t ...time.Time)

	SetDefaultTags(tags map[string]string)
	AddDefaultTag(key, value string)

	Prefix() string
	SetPrefix(prefix string)

	Debug() bool
	SetDebug(enabled bool)
}

// NewAccumulator ...
func NewAccumulator(
	pluginConfig *config.PluginConfig,
	points chan *client.Point,
) Accumulator {
	acc := accumulator{}
	acc.points = points
	acc.pluginConfig = pluginConfig
	return &acc
}

type accumulator struct {
	sync.Mutex

	points chan *client.Point

	defaultTags map[string]string

	debug bool

	pluginConfig *config.PluginConfig

	prefix string
}

// Add ...
func (ac *accumulator) Add(
	measurement string,
	value interface{},
	tags map[string]string,
	t ...time.Time,
) {
	fields := make(map[string]interface{})
	fields["value"] = value
	ac.AddFields(measurement, fields, tags, t...)
}

// AddFields ...
func (ac *accumulator) AddFields(
	measurement string,
	fields map[string]interface{},
	tags map[string]string,
	t ...time.Time,
) {
	// Validate uint64 and float64 fields
	for k, v := range fields {
		switch val := v.(type) {
		case uint64:
			// Todo: support writing uint64
			if val < uint64(9223372036854775808) {
				fields[k] = int64(val)
			} else {
				fields[k] = int64(9223372036854775807)
			}
		case float64:
			// NaNs are invalid values, skip measurement
			if math.IsNaN(val) || math.IsInf(val, 0) {
				if ac.debug {
					log.Printf("Measurement [%s] has a NaN or Inf field, skipping",
						measurement)
				}
				return
			}
		}
	}

	if tags == nil {
		tags = make(map[string]string)
	}

	var timestamp time.Time
	if len(t) > 0 {
		timestamp = t[0]
	} else {
		timestamp = time.Now()
	}

	// asit: we need to round the timestamp to secs
	// opentsdb supports secs (10 digits) or milliseconds (13 digits)

	// todo: use precision capability in points.go

	// change func (p *point) String() string in points.go when this is changed

	// timestamp = timestamp.Round(time.Second)

	// timestamp = timestamp.Truncate(time.Second)
	timestamp = timestamp.Truncate(time.Millisecond)

	if ac.prefix != "" {
		measurement = ac.prefix + measurement
	}

	if ac.pluginConfig != nil {
		if !ac.pluginConfig.Filter.ShouldPass(measurement) || !ac.pluginConfig.Filter.ShouldTagsPass(tags) {
			return
		}
	}

	for k, v := range ac.defaultTags {
		if _, ok := tags[k]; !ok {
			tags[k] = v
		}
	}

	pt, err := client.NewPoint(measurement, tags, fields, timestamp)
	if err != nil {
		log.Printf("Error adding point [%s]: %s\n", measurement, err.Error())
		return
	}
	if ac.debug {
		log.Printf("%s", pt.String())
	}
	ac.points <- pt
}

// SetDefaultTags ...
func (ac *accumulator) SetDefaultTags(tags map[string]string) {
	ac.defaultTags = tags
}

// AddDefaultTag ...
func (ac *accumulator) AddDefaultTag(key, value string) {
	ac.defaultTags[key] = value
}

// Prefix ...
func (ac *accumulator) Prefix() string {
	return ac.prefix
}

func (ac *accumulator) SetPrefix(prefix string) {
	ac.prefix = prefix
}

func (ac *accumulator) Debug() bool {
	return ac.debug
}

func (ac *accumulator) SetDebug(debug bool) {
	ac.debug = debug
}

package nwgraph

import (
	"fmt"
	"opscog/signals/inputs/plugins"
	"opscog/signals/inputs/plugins/kafka"
	"opscog/signals/inputs/plugins/nwgraph/nwmon"
	"opscog/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata"
)

// NWGraph ...
type NWGraph struct {
	//Nothing specific at the moment
}

// NewNWGraph ...
func NewNWGraph() *NWGraph {
	//Check if the nwmon is on if not then launch it
	go GetStarted()
	go getNodeDetails()
	return &NWGraph{}
}

var tagChanged = make(chan bool)
var tags string = ""
var kafkaReadNotify = make(chan bool)

func getNodeDetails() {
	kafka.SubscribeForNotification(&kafkaReadNotify)
	for <-kafkaReadNotify {
		message := kafka.Read()
		if len(message) > 0 {
			//fmt.Printf("NWGraph Got message from kafka %s\n", message)
			obj, err := nwam.GetNWTagsObj(message)
			if err == nil && len(obj.NWSvcInfoList) > 0 {
				//fmt.Printf("NWGraph after message err  %vi len = %d\n", err, len(obj.NWSvcInfoList))
				tags = message
				tagChanged <- true
			}
		}
	}
}

func GetStarted() {
	for <-tagChanged {
		fmt.Printf("Updating nwgraph filter now ...\n")
		nwmon.Run(tags)
	}
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

// SampleConfig ...
func (_ *NWGraph) SampleConfig() string {
	return sampleConfig
}

// Description ...
func (_ *NWGraph) Description() string {
	return "Monitor network connections, decode traffic and stat it"
}

// GatherUnmanagedAsync ...
func (p *NWGraph) GatherUnmanagedAsync(shutdown chan struct{}) error {
	return nil
}

// Gather ...
func (p *NWGraph) Gather(acc plugins.Accumulator) error {
	var stats []nwam.SummaryStruct = nwmon.GetStats()

	//Use channels to communicate
	communicate(acc, stats)
	return nil
}

func communicate(acc plugins.Accumulator, stats []nwam.SummaryStruct) bool {
	//acc.Add(mname, value, p.tags)
	for _, summary := range stats {
		tags := make(map[string]string)
		tags["dst"] = fmt.Sprintf("%s:%s", summary.DstIP, summary.DstPort)
		tags["binding"] = fmt.Sprintf("%s:%s:%s:%d", summary.SrcIP, summary.DstIP, summary.DstPort, summary.StartTime)
		tags["replace_ts"] = fmt.Sprintf("%d", summary.StartTime)
		acc.Add("duration", summary.EndTime-summary.StartTime, tags)
		acc.Add("src_ip", summary.SrcIP, tags)
		acc.Add("dst_ip", summary.DstIP, tags)
		acc.Add("dst_port", summary.DstPort, tags)
		acc.Add("costliest_request_str", summary.CostliestRequestStr, tags)
		acc.Add("topmost_error_request_str", summary.TopmostErrorRequestStr, tags)
		acc.Add("topmost_error_request_count", summary.TopmostErrorRequestCount, tags)
		acc.Add("topmost_error_request_ttfb", summary.TopmostErrorRequestTTFB, tags)
		acc.Add("req_count", summary.ReqCount, tags)
		acc.Add("sent_bytes", summary.SentBytes, tags)
		acc.Add("recv_bytes", summary.RecvBytes, tags)
		tags["key_type"] = "error"
		acc.Add("err_count", summary.ErrorCount, tags)
		delete(tags, "key_type")
		acc.Add("avg_ttfb", summary.AvgTTFB, tags)
		acc.Add("avg_ttlb", summary.AvgTTLB, tags)
		acc.Add("max_ttfb", summary.MaxTTFB, tags)
		acc.Add("max_ttlb", summary.MaxTTLB, tags)
		acc.Add("avg_rsp_size", summary.AvgRspSize, tags)
	}
	return true
}

func init() {
	plugins.Add("nwgraph", func() plugins.Plugin {
		return NewNWGraph()
	})
}

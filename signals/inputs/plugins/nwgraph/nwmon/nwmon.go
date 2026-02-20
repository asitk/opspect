package nwmon

// Copyright 2012 Google, Inc. All rights reserved.
//
// Use of this source code is governed by a BSD-style license
// that can be found in the LICENSE file in the root of the source
// tree.

// This binary provides an example of connecting up bidirectional streams from
// the unidirectional streams provided by gopacket/tcpassembly.

import (
	"flag"
	"fmt"
	"github.com/google/gopacket"
	"github.com/google/gopacket/examples/util"
	"github.com/google/gopacket/layers"
	"github.com/google/gopacket/pcap"
	"github.com/google/gopacket/tcpassembly"
	"log"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/decoders"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/utils"
	"opspect/util/discovery"
	"strconv"
	"time"
)

var iface = flag.String("i", "eth0", "Interface to get packets from")
var snaplen = flag.Int("s", 16<<10, "SnapLen for pcap packet capture")
var logAllPackets = flag.Bool("v", false, "Logs every packet in great detail")

// key is used to map bidirectional streams to each other.
type key struct {
	net, transport gopacket.Flow
}

// String prints out the key in a human-readable fashion.
func (k key) String() string {
	return fmt.Sprintf("%v:%v", k.net, k.transport)
}

// timeout is the length of time to wait befor flushing connections and
// bidirectional stream pairs.
const timeout time.Duration = time.Minute * 1

// myStream implements tcpassembly.Stream
type myStream struct {
	net, transport gopacket.Flow
	bytes          int64 // total bytes seen on this stream.
	//payload [] byte
	bidi *bidi // maps to my bidirectional twin.
	done bool  // if true, we've seen the last packet we're going to for this stream.
	key  string
	role nwamdecoder.Role
	svc  discovery.ServiceCategory
}

// bidi stores each unidirectional side of a bidirectional stream.
//
// When a new stream comes in, if we don't have an opposite stream, a bidi is
// created with 'a' set to the new stream.  If we DO have an opposite stream,
// 'b' is set to the new stream.
type bidi struct {
	key            key       // Key of the first stream, mostly for logging.
	a, b           *myStream // the two bidirectional streams.
	lastPacketSeen time.Time // last time we saw a packet from either stream.
}

// myFactory implements tcpassmebly.StreamFactory
type myFactory struct {
	// bidiMap maps keys to bidirectional stream pairs.
	bidiMap map[key]*bidi
}

// New handles creating a new tcpassembly.Stream.
func (f *myFactory) New(netFlow, tcpFlow gopacket.Flow) tcpassembly.Stream {
	// Create a new stream.
	s := &myStream{net: netFlow, transport: tcpFlow}

	// Find the bidi bidirectional struct for this stream, creating a new one if
	// one doesn't already exist in the map.
	k := key{netFlow, tcpFlow}
	bd := f.bidiMap[k]
	if bd == nil {
		bd = &bidi{a: s, key: k}
		nwamutils.DebugPrint("[%v] created first side of bidirectional stream", bd.key)
		// Register bidirectional with the reverse key, so the matching stream going
		// the other direction will find it.
		f.bidiMap[key{netFlow.Reverse(), tcpFlow.Reverse()}] = bd
	} else {
		nwamutils.DebugPrint("[%v] found second side of bidirectional stream", bd.key)
		bd.b = s
		// Clear out the bidi we're using from the map, just in case.
		delete(f.bidiMap, k)
	}
	s.bidi = bd
	return s
}

// emptyStream is used to finish bidi that only have one stream, in
// collectOldStreams.
var emptyStream = &myStream{done: true}

// collectOldStreams finds any streams that haven't received a packet within
// 'timeout', and sets/finishes the 'b' stream inside them.  The 'a' stream may
// still receive packets after this.
func (f *myFactory) collectOldStreams() {
	cutoff := time.Now().Add(-timeout)
	for k, bd := range f.bidiMap {
		if bd.lastPacketSeen.Before(cutoff) {
			nwamutils.DebugPrint("[%v] timing out old stream", bd.key)
			bd.b = emptyStream   // stub out b with an empty stream.
			delete(f.bidiMap, k) // remove it from our map.
			bd.maybeFinish()     // if b was the last stream we were waiting for, finish up.
		}
	}
}

func getKeyRoleAndSvc(h *myStream) (string, nwamdecoder.Role, discovery.ServiceCategory) {
	if len(h.key) == 0 {
		proto := "tcp"
		srcIP, dstIP := h.net.Endpoints()
		srcPort, dstPort := h.transport.Endpoints()
		srcPortInt, _ := strconv.ParseUint(srcPort.String(), 10, 16)
		dstPortInt, _ := strconv.ParseUint(dstPort.String(), 10, 16)
		h.key, h.role, h.svc = nwam.GetKeyRoleAndSvc(srcIP.String(), dstIP.String(), (int)(srcPortInt), (int)(dstPortInt), proto)
	}
	return h.key, h.role, h.svc
}

// Reassembled handles reassembled TCP stream data.
func (s *myStream) Reassembled(rs []tcpassembly.Reassembly) {
	nwamutils.DebugPrint("==============================")
	nwamutils.DebugPrint("Enter REASSEMBLED\n")
	hkey, role, svc := getKeyRoleAndSvc(s)
	nwamutils.DebugPrint("Got hkey = %s and role = %d", hkey, role)
	for _, r := range rs {
		// For now, we'll simply count the bytes on each side of the TCP stream.
		if len(r.Bytes) > 0 {
			//You can process inline but heavy processing should be avoided and pushed to a goroutine
			nwam.ProcessStreamData(hkey, svc, role, len(r.Bytes), r.Bytes, r.Seen)
			//nwamutils.DebugPrint("Write Success")
		}
		s.bytes += int64(len(r.Bytes))
		//nwamutils.DebugPrint("=========skipped = %d", r.Skip)
		if r.Skip > 0 {
			s.bytes += int64(r.Skip)
		}
		// Mark that we've received new packet data.
		// We could just use time.Now, but by using r.Seen we handle the case
		// where packets are being read from a file and could be very old.
		if s.bidi.lastPacketSeen.After(r.Seen) {
			s.bidi.lastPacketSeen = r.Seen
		}
	}
	nwamutils.DebugPrint("==============================")
}

// ReassemblyComplete marks this stream as finished.
func (s *myStream) ReassemblyComplete() {
	nwamutils.DebugPrint("========ReassemblyComplete======================")
	var rx, tx int64
	s.done = true
	bd := s.bidi
	hkey, role, _ := getKeyRoleAndSvc(s)
	switch role {
	case nwamdecoder.Client:
		{
			rx = bd.a.bytes
			tx = bd.b.bytes
		}
	case nwamdecoder.Server:
		{
			rx = bd.b.bytes
			tx = bd.a.bytes
		}
	}

	nwam.MarkEnd(hkey, tx, rx)
	s.bidi.maybeFinish()
}

// maybeFinish will wait until both directions are complete, then print out
// stats.
func (bd *bidi) maybeFinish() {
	switch {
	case bd.a == nil:
		log.Fatalf("[%v] a should always be non-nil, since it's set when bidis are created", bd.key)
	case !bd.a.done:
		nwamutils.DebugPrint("[%v] still waiting on first stream", bd.key)
	case bd.b == nil:
		nwamutils.DebugPrint("[%v] no second stream yet", bd.key)
	case !bd.b.done:
		nwamutils.DebugPrint("[%v] still waiting on second stream", bd.key)
	default:
		nwamutils.DebugPrint("[%v] FINISHED, bytes: %d tx, %d rx", bd.key, bd.a.bytes, bd.b.bytes)
	}
}

var bailout bool = false
var assembler *tcpassembly.Assembler = nil
var streamFactory *myFactory = nil
var handle *pcap.Handle = nil

func start(tags string) {
	defer util.Run()
	var err error = nil
	bailout = false
	runstats.IsActive = true
	runstats.lastTags = tags
	runstats.lastProcessedSummary = time.Now()
	log.Printf("starting capture on interface %q", *iface)
	// Set up pcap packet capture
	handle, err = pcap.OpenLive(*iface, int32(*snaplen), true, pcap.BlockForever)
	if err != nil {
		panic(err)
	}
	var nwtags = tags

	var filter_expr string
	if nwam.Init(nwtags) {
		filter_expr = nwam.GetNWFilter()
	}
	nwamutils.DebugPrint("The filter_expr = %s", filter_expr)

	var filter = flag.String("f", filter_expr, "BPF filter for pcap")
	if err := handle.SetBPFFilter(*filter); err != nil {
		//panic(err) Cant kill the process as the agent will die otherwise
		runstats.IsActive = false
		return
	}

	// Set up assembly
	streamFactory = &myFactory{bidiMap: make(map[key]*bidi)}
	streamPool := tcpassembly.NewStreamPool(streamFactory)
	assembler = tcpassembly.NewAssembler(streamPool)

	nwamutils.DebugPrint("reading in packets")

	// Read in packets, pass to assembler.
	packetSource := gopacket.NewPacketSource(handle, handle.LinkType())
	packets := packetSource.Packets()
	var count int = 0
	ticker := time.Tick(timeout)
	for {
		if bailout {
			nwamutils.DebugPrint("Getting bailed out")
			break
		}
		select {
		case packet := <-packets:
			count++
			if *logAllPackets {
				nwamutils.DebugPrint("%v", packet)
			}
			if packet.NetworkLayer() == nil || packet.TransportLayer() == nil || packet.TransportLayer().LayerType() != layers.LayerTypeTCP {
				nwamutils.DebugPrint("Unusable packet")
				continue
			}
			tcp := packet.TransportLayer().(*layers.TCP)
			if tcp != nil {
				nwamutils.DebugPrint("Got tcp packet %d", count)
			}
			assembler.AssembleWithTimestamp(packet.NetworkLayer().NetworkFlow(), tcp, packet.Metadata().Timestamp)
			nwamutils.DebugPrint("Finished assembly tcp packet %d ", count)

		case <-ticker:
			// Every minute, flush connections that haven't seen activity in the past minute.
			nwamutils.DebugPrint("---- FLUSHING ----")
			assembler.FlushOlderThan(time.Now().Add(-timeout))
			streamFactory.collectOldStreams()
		}
	}
}

func stop() {
	if !bailout && runstats.IsActive {
		bailout = true
		nwamutils.DebugPrint("Stopping it  ----")
		assembler.FlushOlderThan(time.Now().Add(-timeout))
		streamFactory.collectOldStreams()
		nwam.Close()
		handle.Close()
	}
}

type RunStats struct {
	IsActive             bool
	lastTags             string
	lastProcessedSummary time.Time
}

var runstats RunStats

func Run(tags string) {
	//Check to see if its already running. If so then just return
	if runstats.lastTags != tags {
		nwamutils.DebugPrint("Starting it again...")
		stop()
		start(tags)
	}
}

func GetStats() []nwam.SummaryStruct {
	t1 := time.Now().Second()
	//Using 19s as the interval
	if (t1 + 19) > 59 {
		ret := nwam.ProcessSummary(runstats.lastProcessedSummary)
		runstats.lastProcessedSummary = time.Now()
		return ret
	} else {
		var summaryList []nwam.SummaryStruct
		return summaryList
	}
}

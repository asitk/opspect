// The package implements processing data and creating stats for it
package nwam

import (
	"fmt"
	//"log"
	"strings"
	"time"

	"opscog/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/decoders"
	"opscog/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/utils"

	"opscog/util/discovery"
)

var hmap map[string]ioState

type reqDetails struct {
	ttfb int64
	ttlb int64
	data []byte
	len  int
}
type respDetails struct {
	ttfb   int64
	ttlb   int64
	len    int
	is_err bool
}
type ioDetails struct {
	req reqDetails
	rsp respDetails
}
type ioState struct {
	req_index int
	rsp_index int
	req_data  []byte
	rsp_data  []byte
	iod       []ioDetails
	is_closed bool
	tx        int64
	rx        int64
}

func createMap(key string, pkt_timestamp time.Time) {
	ios := hmap[key]
	byArr := []byte("")
	//epoch := pkt_timestamp.UnixNano() / 1000000
	//epoch *= 1000000
	//TODO: Response should not have a timestamp till it gets one
	var epoch int64 = 0
	req_det := reqDetails{epoch, epoch, byArr, 0}
	rsp_det := respDetails{epoch, epoch, 0, false}
	iod := ioDetails{req_det, rsp_det}
	ios.iod = append(ios.iod, iod)
	hmap[key] = ios
}

func ProcessStreamData(hkey string, svc discovery.ServiceCategory, role nwamdecoder.Role, n int, byArr []byte, pkt_timestamp time.Time) {
	d := nwamdecoder.GetDecoderType(svc)
	dec := nwamdecoder.GetDecoder(d)
	nwamutils.DebugPrint("In Processdata Got %s of length %d", nwamdecoder.RoleToString(role), n)
	//Check for previous data first and append to it
	if len(hmap) == 0 {
		hmap = make(map[string]ioState)
	}

	var seq_id int = -1
	ios := hmap[hkey]

	var prev_len int
	var prev_data []byte
	if role == nwamdecoder.Client {
		prev_len = len(ios.req_data)
		prev_data = ios.req_data
		ios.req_data = nil
	} else {
		prev_len = len(ios.rsp_data)
		prev_data = ios.rsp_data
		ios.rsp_data = nil
	}
	hmap[hkey] = ios
	if prev_len > 0 {
		nwamutils.DebugPrint("%s with prev_len = %d", nwamdecoder.RoleToString(role), prev_len)
	}
	byteArr := make([]byte, prev_len+n)
	if prev_len > 0 {
		copy(byteArr, prev_data)
	}
	copy(byteArr[prev_len:], byArr)

	var m int
	var len int
	hmap[hkey] = ios
	n += prev_len
	var ptr int = 0
	//nwamutils.DebugPrint("%s data = %v",role,byteArr[ptr:n])
	for ptr = 0; ptr < n; {
		if (n - ptr) >= 4 {
			len, seq_id, m = nwamdecoder.GetLengthAndSeqId(dec, byteArr, ptr)
			nwamutils.DebugPrint("%s len = %d and seqid = %d", nwamdecoder.RoleToString(role), len, seq_id)
			ptr += m
			if len == 0 {
				if m == 0 { //Else it will lead to infinite loop
					break
				}
				continue
			}
		} else {
			copyExcessData(hkey, role, ptr, byteArr)
			nwamutils.DebugPrint("%s in excess data region ptr = %d size = %d", nwamdecoder.RoleToString(role), ptr, n)
			break
		}

		if (ptr + len) > n {
			copyExcessData(hkey, role, ptr-m, byteArr)
			nwamutils.DebugPrint("%s in excess data region ptr = %d length = %d size = %d", nwamdecoder.RoleToString(role), ptr-m, len, n)
			break
		}

		is_req, is_err, is_new, data := nwamdecoder.ProcessPkt(dec, role, len, seq_id, byteArr[ptr:], pkt_timestamp)
		addEntry(hkey, is_req, role, len, data, is_new, is_err, pkt_timestamp)
		ptr += len

	}
}

func copyExcessData(hkey string, role nwamdecoder.Role, ptr int, byteArr []byte) {
	ba := make([]byte, len(byteArr)-ptr)
	n := copy(ba, byteArr[ptr:])
	if n > 0 {
		nwamutils.DebugPrint("%s Got excess data %d bytes for next cycle", nwamdecoder.RoleToString(role), n)
		ios := hmap[hkey]
		switch role {
		case nwamdecoder.Client:
			ios.req_data = ba
		case nwamdecoder.Server:
			ios.rsp_data = ba
		}
		hmap[hkey] = ios
	}
}

func addEntry(hkey string, is_req bool, role nwamdecoder.Role, n int, ba string, is_new bool, is_err bool, pkt_timestamp time.Time) {
	nwamutils.DebugPrint("Role = %d, is_req = %v is_new = %v is_err = %v n = %d", role, is_req, is_new, is_err, n)
	epoch := pkt_timestamp.UnixNano()

	ios := hmap[hkey]

	li := len(ios.iod)
	hmap[hkey] = ios
	if role == nwamdecoder.Server && ios.req_index > 0 {
		if is_req {
			is_req = false
			is_new = false
		}
		if ios.rsp_index == ios.req_index && is_new {
			is_new = false
		}

	}
	if is_new && (is_req && ios.req_index > (li-1)) || (!is_req && ios.rsp_index > (li-1)) {
		nwamutils.DebugPrint("Creating map ")
		createMap(hkey, pkt_timestamp)
	}
	ios = hmap[hkey]
	iod := ios.iod
	li = len(iod)

	if is_req {
		if is_new {
			ios.req_index++
		}
		req_det := &iod[ios.req_index-1].req
		if is_new {
			req_det.ttfb = epoch
		}
		byArr := []byte(ba)
		req_det.data = byArr
		req_det.ttlb = epoch
		ios.rx += int64(n)
		req_det.len += n
	}

	if !is_req && ios.rsp_index <= li {
		if is_new || ios.rsp_index == 0 {
			ios.rsp_index++
		}
		rsp_det := &iod[ios.rsp_index-1].rsp
		if is_new {
			rsp_det.ttfb = epoch
		}
		rsp_det.ttlb = epoch
		ios.tx += int64(n)
		rsp_det.len += n

		rsp_det.is_err = is_err
	}
	hmap[hkey] = ios
	nwamutils.DebugPrint("rsp_index = %d req_index = %d\n", ios.rsp_index, ios.req_index)
}

type SummaryStruct struct {
	StartTime                int64  `json:"start_time"`
	EndTime                  int64  `json:"end_time"`
	SrcIP                    string `json:"src_ip"`
	DstIP                    string `json:"dst_ip"`
	DstPort                  string `json:"dst_port"`
	ReqCount                 int    `json:"req_count"`
	SentBytes                int64  `json:"send_bytes"`
	RecvBytes                int64  `json:"recv_bytes"`
	ErrorCount               int    `json:"err_count"`
	AvgTTFB                  int64  `json:"avg_ttfb"`
	AvgTTLB                  int64  `json:"avg_ttlb"`
	MaxTTFB                  int64  `json:"max_ttfb"`
	MaxTTLB                  int64  `json:"max_ttlb"`
	AvgRspSize               int    `json:"avg_rsp_size"`
	CostliestRequestStr      []byte `json:"costliest_request_str"`
	TopmostErrorRequestStr   []byte `json:"topmost_error_request_str"`
	TopmostErrorRequestCount int    `json:"topmost_error_request_count"`
	TopmostErrorRequestTTFB  int64  `json:"topmost_error_request_ttfb"`
}

func MarkEnd(hkey string, tx_len int64, rx_len int64) {
	ios := hmap[hkey]
	if ios.tx != 0 && ios.rx != 0 {
		ios.is_closed = true
		ios.tx = tx_len
		ios.rx = rx_len
		hmap[hkey] = ios
	}
}
func getTimeDiff(a int64, b int64) int64 {
	var c int64
	if a > b {
		c = a - b
	} else {
		c = b - a
	}
	return c
}

func processErrorStat(ss *SummaryStruct, iod ioDetails) {
	if iod.rsp.is_err {
		if ss.TopmostErrorRequestTTFB < getTimeDiff(iod.rsp.ttfb, iod.req.ttlb) {
			ss.TopmostErrorRequestTTFB = getTimeDiff(iod.rsp.ttfb, iod.req.ttlb)
			if len(ss.TopmostErrorRequestStr) > 0 && string(ss.TopmostErrorRequestStr) == string(iod.req.data) {
				ss.TopmostErrorRequestCount++
			} else {
				ss.TopmostErrorRequestStr = make([]byte, len(iod.req.data))
				copy(ss.TopmostErrorRequestStr, iod.req.data)
				ss.TopmostErrorRequestCount = 1
			}
		}
	}
}

func ProcessSummary(lastTime time.Time) []SummaryStruct {
	summaryMap := make(map[string]SummaryStruct)
	for k, v := range hmap {
		found := false
		src := strings.Split(k, ">")[0]
		dst := strings.Split(k, ">")[1]
		srcIp := strings.Split(src, ":")[0]
		dstIp := strings.Split(dst, ":")[0]
		dstPort := strings.Split(dst, ":")[1]
		key := fmt.Sprintf("%s:%s<%s", dstIp, dstPort, srcIp)
		ss := summaryMap[key]
		if len(ss.DstPort) == 0 {
			ss.SrcIP = srcIp
			ss.DstIP = dstIp
			ss.DstPort = dstPort
			ss.StartTime = lastTime.UnixNano()
			ss.EndTime = 0
		}

		iod := v.iod
		nwamutils.DebugPrint("Got len of iod as %d", len(iod))
		for i := 0; i < len(iod); i++ {
			nwamutils.DebugPrint("+%v\n", iod[i])
			//if iod[i].req.ttfb > 0 && iod[i].req.ttfb >= ss.StartTime && iod[i].rsp.ttfb > 0 && iod[i].rsp.ttlb <= ss.EndTime {
			//TODO: Need to revisit this behaviour
			if iod[i].req.ttfb > 0 && iod[i].req.ttfb >= ss.StartTime && iod[i].rsp.ttfb > 0 {
				found = true
				if ss.StartTime > iod[i].req.ttfb {
					ss.StartTime = iod[i].req.ttfb
				}
				if ss.EndTime < iod[i].rsp.ttlb {
					ss.EndTime = iod[i].rsp.ttlb
				}
				ss.ReqCount++
				if iod[i].rsp.is_err {
					processErrorStat(&ss, iod[i])
					ss.ErrorCount++
				}
				ss.AvgTTFB += getTimeDiff(iod[i].rsp.ttfb, iod[i].req.ttlb)
				ss.AvgTTLB += getTimeDiff(iod[i].rsp.ttlb, iod[i].req.ttlb)
				ss.AvgRspSize += iod[i].rsp.len
				nwamutils.DebugPrint("The req = %s and time = %d", string(iod[i].req.data), getTimeDiff(iod[i].rsp.ttfb, iod[i].req.ttlb))
				if ss.MaxTTFB < getTimeDiff(iod[i].rsp.ttfb, iod[i].req.ttlb) {
					nwamutils.DebugPrint("Replacing max value of %d with %d", ss.MaxTTFB, getTimeDiff(iod[i].rsp.ttfb, iod[i].req.ttlb))
					ss.MaxTTFB = getTimeDiff(iod[i].rsp.ttfb, iod[i].req.ttlb)
					ss.MaxTTLB = getTimeDiff(iod[i].rsp.ttlb, iod[i].req.ttlb)
					nwamutils.DebugPrint("Replacing max req = %s with %s", string(ss.CostliestRequestStr), string(iod[i].req.data))
					ss.CostliestRequestStr = make([]byte, len(iod[i].req.data))
					copy(ss.CostliestRequestStr, iod[i].req.data)
					nwamutils.DebugPrint("Now max req = %s", string(ss.CostliestRequestStr))
				}
			}
		}
		tmp := ss.AvgTTFB
		if tmp > ss.AvgTTLB {
			ss.AvgTTFB = ss.AvgTTLB
			ss.AvgTTLB = tmp
		}
		ss.SentBytes += v.tx
		ss.RecvBytes += v.rx

		if v.is_closed {
			nwamutils.DebugPrint("Removing closed tuple from hmap")
			delete(hmap, k)
		}
		if found {
			summaryMap[key] = ss
		}
	}

	var summaryList []SummaryStruct
	for k, v := range summaryMap {
		nwamutils.DebugPrint("%s", k)
		v.StartTime /= 1000000
		v.EndTime /= 1000000
		v.AvgRspSize /= v.ReqCount
		v.AvgTTFB /= int64(v.ReqCount * 1000000)
		v.AvgTTLB /= int64(v.ReqCount * 1000000)
		v.MaxTTFB /= 1000000
		v.MaxTTLB /= 1000000
		summaryList = append(summaryList, v)
	}

	return summaryList
}

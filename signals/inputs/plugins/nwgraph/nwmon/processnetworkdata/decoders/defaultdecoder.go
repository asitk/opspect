// The package implements processing data and creating stats for it
package nwamdecoder

import (
	//    "fmt"
	//    "log"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/utils"
	"time"
)

type UnknownProtoStruct struct{}

func (s *UnknownProtoStruct) getLengthAndSeqID(byArr []byte, ptr int) (int, int, int) {
	return int(len(byArr)), 0, 0
}

func (s *UnknownProtoStruct) processPkt(role Role, len int, seq_id int, byteArr []byte, pkt_timestamp time.Time) (bool, bool, bool, string) {
	role_str := RoleToString(role)
	nwamutils.DebugPrint("Got %v of length %v", role_str, len)
	var is_err bool = false
	var is_req bool = false
	var is_new bool = false
	if len > 0 && role == Client {
		is_req = true
	}
	if len == 0 {
		is_err = true
	}
	is_new = true
	data := nwamutils.Convert_to_string(byteArr, len)
	nwamutils.DebugPrint("In UnknownProto data = %s", data)
	return is_req, is_err, is_new, data
}

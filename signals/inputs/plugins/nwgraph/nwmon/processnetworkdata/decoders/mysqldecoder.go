// The package implements processing data and creating stats for it
package nwamdecoder

import (
	"opscog/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/utils"
	"time"
)

type MySQLStruct struct{}

func (s *MySQLStruct) getLengthAndSeqID(byArr []byte, ptr int) (int, int, int) {
	var len1 int32 = 0
	var len2 int32 = 0
	var len3 int32 = 0
	var seqid int
	var length int32
	var m int = 4
	len1 = int32(byArr[ptr+2])
	len1 = len1 << 16
	len2 = int32(byArr[ptr+1])
	len2 = len2 << 8
	len3 = int32(byArr[ptr])
	length = len1 + len2 + len3
	if length > 1000 {
		nwamutils.DebugPrint("Funny length %d", length)
	}
	seqid = int(byArr[ptr+3])

	return int(length), seqid, m
}

func (s *MySQLStruct) processPkt(role Role, len int, seq_id int, byteArr []byte, pkt_timestamp time.Time) (bool, bool, bool, string) {
	role_str := RoleToString(role)
	nwamutils.DebugPrint("Got %v of length %v", role_str, len)
	var is_err bool = false
	var is_req bool = false
	var is_new bool = false
	if len >= 7 && byteArr[0] == 0x00 {
		nwamutils.DebugPrint("%s Got OK packet of len = %d", role_str, len)
	}
	if len < 9 && byteArr[0] == 0xfe {
		nwamutils.DebugPrint("%s Got EOF packet of len = %d", role_str, len)
	}
	if byteArr[0] == 0xff {
		nwamutils.DebugPrint("%s Got Err packet of len = %d", role_str, len)
		is_err = true
	}
	if seq_id == 0 {
		if byteArr[0] < 0x20 {
			nwamutils.DebugPrint("%s Got Cmd %x packet of len = %d", role_str, byteArr[0], len)
			is_req = true
		}
	}
	if seq_id < 2 {
		is_new = true
	}
	data := nwamutils.Convert_to_string(byteArr, len)
	nwamutils.DebugPrint("In Mysql data = %s", data)
	return is_req, is_err, is_new, data
}

//The package implements processing data and creating stats for it
package nwamdecoder
import (
    "time"
    "bitbucket.org/infrared/util/discovery"
)

type Role int
const (
    Server Role = 1 + iota
    Client
)
func RoleToString(r Role) string {
    var role_str  string
    switch(r) {
        case Server: role_str = "Server"
        case Client: role_str = "Client"
    }
    return role_str
}

type Decoder int
const (
    MySQL Decoder = 1 + iota
    Unknown
)

func GetDecoderType(svc discovery.ServiceCategory) Decoder {
    var d Decoder = Unknown
    switch(svc) {
        case discovery.TypeRDBMS : d = MySQL
    }
    
    return d
}

type NWDecoder interface {
    getLengthAndSeqID(byteArr [] byte,offset int) (int, int , int)
    processPkt(role Role, len int, seq_id int, byteArr [] byte, ts time.Time) (bool, bool, bool, string)
}

func GetLengthAndSeqId(d NWDecoder, byteArr [] byte,offset int) (int, int , int) {
    return d.getLengthAndSeqID(byteArr, offset)
}

func ProcessPkt(d NWDecoder, role Role, len int, seq_id int, byteArr [] byte, ts time.Time) (bool, bool, bool, string) {
    return d.processPkt(role, len, seq_id, byteArr, ts)
}

func GetDecoder(d Decoder) NWDecoder {
    var nwd NWDecoder = nil
    switch(d) {
        case MySQL :  nwd = &MySQLStruct{}
        case Unknown : nwd = &UnknownProtoStruct{}
    }
    return nwd
}

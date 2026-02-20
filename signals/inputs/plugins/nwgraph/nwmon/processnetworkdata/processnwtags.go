package nwam

import (
	"encoding/json"
	"fmt"
	"opspect/signals/inputs/plugins/nwgraph/nwmon/processnetworkdata/decoders"
	"opspect/util/discovery"
)

type NodeDetail struct {
	CustID       string `json:"customer_id"`
	DeploymentID string `json:"deployment_id"`
	ClusterID    string `json:"cluster_id"`
}

type NWSvcTag struct {
	ClusterID string                    `json:"cluster_id"`
	SvcName   string                    `json:"name"`
	Svc       discovery.ServiceCategory `json:"svc"`
	IPVer     discovery.IPVer           `json:"ipver"`
	Proto     discovery.Protocol        `json:"proto"`
	Port      int                       `json:"port"`
	Interface string                    `json:"interface"`
}
type NWTags struct {
	NodeDetails   map[string]NodeDetail `json:"nodeDetails"`
	NWSvcInfoList []NWSvcTag            `json:"nwSvcInfoList"`
}

type NWPortTag struct {
	Port  int
	Proto string
}

func GetNWTagsObj(tagStr string) (NWTags, error) {
	var tagStrBlob = []byte(tagStr)
	var nwObj NWTags
	err := json.Unmarshal(tagStrBlob, &nwObj)
	//fmt.Printf("The nwObj = %v", nwObj)
	return nwObj, err
	// str, er := json.MarshalIndent(&nwObj, "", "\t")
}

var tags string
var inited bool

var portmap map[NWPortTag]NWSvcTag

func Init(tagStr string) bool {
	status := true
	if !inited {
		tags = tagStr
		populateportmap()
		inited = true
	} else {
		status = false
	}

	return status
}

func Close() bool {
	status := true
	if !inited {
		status = false
	}

	if len(portmap) > 0 {
		for k, _ := range portmap {
			delete(portmap, k)
		}
		inited = false
		tags = ""
		portmap = nil
	}

	return status
}

func populateportmap() {
	portmap = make(map[NWPortTag]NWSvcTag)
	nwObj, err := GetNWTagsObj(tags)

	if err == nil {
		for _, v := range nwObj.NWSvcInfoList {
			v.Proto = discovery.TypeTCP //TODO: Remove this once a new run is done as the bug is corrected
			k := NWPortTag{Port: v.Port, Proto: discovery.GetProtocolString(v.Proto)}
			portmap[k] = v
		}
	} else {
		fmt.Println("error:", err)
		fmt.Printf("%+v\n", nwObj)
	}
}

func GetNWFilter() string {
	var filter string
	for _, v := range portmap {
		//Using lucene syntax
		if len(filter) == 0 {
			filter = fmt.Sprintf("(%s and port %d)", discovery.GetProtocolString(v.Proto), v.Port)
		} else {
			filter = fmt.Sprintf("%s or (%s and port %d)", filter, discovery.GetProtocolString(v.Proto), v.Port)
		}
	}

	return filter
}

func GetKeyRoleAndSvc(srcIP string, dstIP string, srcPort int, dstPort int, proto string) (string, nwamdecoder.Role, discovery.ServiceCategory) {
	var hkey string
	var role nwamdecoder.Role
	v := portmap[NWPortTag{Port: srcPort, Proto: proto}]
	if len(v.SvcName) > 0 {
		hkey = fmt.Sprintf("%s:%d>%s:%d", dstIP, dstPort, srcIP, srcPort)
		role = nwamdecoder.Server
	} else {
		v = portmap[NWPortTag{Port: dstPort, Proto: proto}]
		hkey = fmt.Sprintf("%s:%d>%s:%d", srcIP, srcPort, dstIP, dstPort)
		role = nwamdecoder.Client
	}
	return hkey, role, v.Svc
}

/*
func main() {
var jsonBlob string = `
{
   "nwSvcInfoList": [
		{
			"cluster_id": "myappcluster",
			"name": "apache2",
			"svc": 0,
			"ipver": 1,
			"proto": 2,
			"port": 80,
			"interface": "*"
		}
	]
}
    `
    filter, hmap := GetNWFilterFromTags(jsonBlob)
    log.Println("Filter = ", filter)
    fmt.Printf("%+v\n", hmap)
}
*/

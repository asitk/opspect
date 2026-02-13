//
// discovery.go
// Copyright (C) 2016 asitk <asitk@ak-ubuntu>
//
// Distributed under terms of the MIT license.
//

package discovery

import (
	"errors"
	"fmt"
	"io/ioutil"
	"os/exec"
	"strconv"
	"strings"

	log "github.com/Sirupsen/logrus"
)

// ServiceCategory ...
// Types of Services
type ServiceCategory int

const (
	TypeWebServer ServiceCategory = iota
	TypeAppServer
	TypeQueue
	TypeCache
	TypeRDBMS
	TypeBigTable
	TypeKVStore
	TypeDOCStore
	TypeGraphStore
)

// Protocol ...
// IPV4 / IPV6
type Protocol int

const (
	TypeTCP Protocol = iota
	TypeUDP
	UndefinedProtocol
)

// IPVer ...
// IP Version
type IPVer int

const (
	Ipv4 IPVer = iota
	Ipv6
	UndefinedVer
)

// PortInfo ...
// Port information
type PortInfo struct {
	Ppid  int // asit todo: move this to upper struct
	Ipver IPVer
	Proto Protocol
	Ip    string
	Port  int
}

// ProcInfoSnapshot ...
// This is a container for discovered processes
type ProcInfoSnapshot struct {
	Pid          int
	Category     ServiceCategory
	Name         string
	Portinfolist []PortInfo
}

type Specification struct {
	PidFile  string
	Exe      string
	Prefix   string
	Pattern  string
	Category int
	Name     string
}

func GetProtocolString(proto Protocol) string {
	var protoStr string
	switch proto {
	case TypeTCP:
		protoStr = "tcp"
	case TypeUDP:
		protoStr = "udp"
	case UndefinedProtocol:
		protoStr = "undefined"
	}
	return protoStr
}

// PidsFromFile ...
func PidsFromFile(file string) ([]int32, error) {
	var out []int32
	var outerr error
	pidString, err := ioutil.ReadFile(file)
	if err != nil {
		outerr = fmt.Errorf("Failed to read pidfile '%s'. Error: '%s'", file, err)
	} else {
		pid, err := strconv.Atoi(strings.TrimSpace(string(pidString)))
		if err != nil {
			outerr = err
		} else {
			out = append(out, int32(pid))
		}
	}
	return out, outerr
}

// PidsFromExe ...
// Get Pids from Exe file
func PidsFromExe(exe string) ([]int32, error) {
	var out []int32
	var outerr error

	pgrep, err := exec.Command("pgrep", exe).Output()
	if err != nil {
		return out, err
	}

	pids := strings.Fields(string(pgrep))
	for _, pid := range pids {
		ipid, err := strconv.Atoi(pid)
		if err == nil {
			out = append(out, int32(ipid))
		} else {
			outerr = err
		}
	}

	return out, outerr
}

// PidsFromPattern ...
// Get pid list
func PidsFromPattern(pattern string) ([]int32, error) {
	var out []int32
	var outerr error

	pgrep, err := exec.Command("pgrep", "-f", pattern).Output()
	if err != nil {
		return out, fmt.Errorf("pgrep error: '%s'", err)
	}

	pids := strings.Fields(string(pgrep))
	for _, pid := range pids {
		ipid, err := strconv.Atoi(pid)
		if err == nil {
			out = append(out, int32(ipid))
		} else {
			outerr = err
		}
	}

	return out, outerr
}

// PortsFromPid ...
// Get portinfolist from Pid
// COMMAND    PID PPID USER   FD   TYPE DEVICE SIZE/OFF NODE NAME
// redis-ser 3499    1 root    4u  IPv6  14239      0t0  TCP *:1802 (LISTEN)
// redis-ser 3499    1 root    5u  IPv4  14240      0t0  TCP *:1802 (LISTEN)
func PortsFromPid(pid int) ([]PortInfo, error) {
	var portinfolist []PortInfo
	var err error

	cmd := fmt.Sprintf("lsof -PbanR -w -iTCP -iUDP -p %s | grep -i listen", strconv.Itoa(pid))
	lsof, err := exec.Command("/bin/sh", "-c", cmd).Output()
	if err != nil {
		return portinfolist, err
	}

	lines := strings.Split(string(lsof), "\n")
	for _, line := range lines {
		fields := strings.Fields(line)
		var pi PortInfo

		// last elem
		if len(fields) <= 1 {
			break
		}

		// PPID
		pi.Ppid, _ = strconv.Atoi(fields[2])

		// IPVer
		switch strings.ToLower(fields[5]) {
		case "ipv4":
			pi.Ipver = Ipv4
		case "ipv6":
			pi.Ipver = Ipv6
		default:
			pi.Ipver = UndefinedVer
		}

		// Protocol
		switch strings.ToLower(fields[8]) {
		case "tcp":
			pi.Proto = TypeTCP
		case "udp":
			pi.Proto = TypeUDP
		default:
			pi.Proto = UndefinedProtocol
		}

		port := strings.Split(fields[9], ":")
		pi.Ip = port[0]
		pi.Port, _ = strconv.Atoi(port[1])

		portinfolist = append(portinfolist, pi)
	}

	return portinfolist, err
}

func updatePI(pids *[]int32, procinfolist *[]ProcInfoSnapshot,
	procinfo *ProcInfoSnapshot, category ServiceCategory,
	name string) error {
	var err error
	var portinfolist []PortInfo

	for _, v := range *pids {
		pid := int(v)

		// for each id get the ports
		portinfolist, err = PortsFromPid(pid)
		if err != nil {
			errstr := fmt.Sprintf("lsof %v on pid %d", err, pid)
			err := errors.New(errstr)
			return err
		}

		procinfo.Pid = pid
		procinfo.Category = category
		procinfo.Name = name
		procinfo.Portinfolist = portinfolist

		*procinfolist = append(*procinfolist, *procinfo)
	}

	return err
}

// GetProcessSnapshotFromExename ...
func GetProcessSnapshotFromExename(exeName string) ([]ProcInfoSnapshot, error) {
	var procinfolist []ProcInfoSnapshot
	var procinfo ProcInfoSnapshot

	if len(exeName) == 0 {
		log.Printf("No exe name provided. Bailing out...")
	}

	// Locate the exeName
	pids, err := PidsFromExe(exeName)
	if err != nil {
		log.Printf("%s pgrep %v", exeName, err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeWebServer, exeName)
		if err != nil {
			log.Printf("%s updatePI %v", exeName, err)
		}
	}

	// Node.JS
	for _, v := range procinfolist {
		log.Println(v)
	}

	return procinfolist, err
}

// GetProcessSnapshot ...
func GetProcessSnapshot(Specifications []Specification) ([]ProcInfoSnapshot, error) {
	var procinfolist []ProcInfoSnapshot
	var procinfo ProcInfoSnapshot

	// Locate webservers (Apache, Nginx)
	pids, err := PidsFromExe("apache2")
	if err != nil {
		log.Printf("apache2 pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeWebServer, "apache2")
		if err != nil {
			log.Printf("apache2 updatePI %v", err)
		}
	}
	pids, err = PidsFromExe("nginx")
	if err != nil {
		log.Printf("nginx pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeWebServer, "nginx")
		if err != nil {
			log.Printf("nginx updatePI % %v", err)
		}
	}

	// Locate Cache (Redis/Memcached)
	pids, err = PidsFromExe("redis-server")
	if err != nil {
		log.Printf("redis-server pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeCache, "redis-server")
		if err != nil {
			log.Printf("redis-server updatePI % %v", err)
		}
	}
	pids, err = PidsFromExe("memcached")
	if err != nil {
		log.Printf("memcached pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeCache, "memcached")
		if err != nil {
			log.Printf("memcached updatePI % %v", err)
		}
	}

	// asit todo: pgrep does not support lookarounds
	// ^(?=.*\b-Dkafka\.logs\.dir\b)(?=.*\bkafka.[0-9]\.[0-9][0-9].\.jar\b).*$
	// this fingerprint will match if -DKafka.logs.dir & kafka_2.10.jar occur
	// in * any * order using lookarounds. Currently just relying on a single
	// fingerprint till this is resolved

	// Locate Queue (MQTT/RabbitMQ/Kafka)
	pids, err = PidsFromPattern("^.*\\-Dkafka\\.logs\\.dir.*$")
	if err != nil {
		log.Printf("kafka pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeQueue, "kafka")
		if err != nil {
			log.Printf("kafka updatePI %%v", err)
		}
	}

	// Locate DB (MySQL, PostGres, MongoDB, CouchBase, MS-SQL Server, Oracle)
	pids, err = PidsFromExe("mysqld")
	if err != nil {
		log.Printf("mysqld pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeRDBMS, "mysqld")
		if err != nil {
			log.Printf("mysqld updatePI %p %v", err)
		}
	}
	pids, err = PidsFromExe("postgres")
	if err != nil {
		log.Printf("postgres pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeRDBMS, "postgres")
		if err != nil {
			log.Printf("postgres updatePI %v", err)
		}
	}
	pids, err = PidsFromExe("mongod")
	if err != nil {
		log.Printf("mongod pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeDOCStore, "mongod")
		if err != nil {
			log.Printf("mongod updatePI %%v", err)
		}
	}
	pids, err = PidsFromExe("couchbase")
	if err != nil {
		log.Printf("couchbase pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeDOCStore, "couchbase")
		if err != nil {
			log.Printf("couchbase %updatePI %v", err)
		}
	}

	// Locate App Servers (Jetty, Netty, Tomcat, IIS)
	pids, err = PidsFromPattern("^.*((jetty\\.logs).*(start\\.jar))|((start\\.jar).*(jetty\\.logs)).*")
	if err != nil {
		log.Printf("jetty pgrep %v", err)
	} else {
		err = updatePI(&pids, &procinfolist, &procinfo, TypeWebServer, "jetty")
		if err != nil {
			log.Printf("jetty updatePI %%v", err)
		}
	}

	for i := 0; i < len(Specifications); i++ {
		log.Printf("user spec: %s %s", Specifications[i].Name, Specifications[i].Pattern)

		pids, err = PidsFromPattern(Specifications[i].Pattern)
		if err != nil {
			log.Printf("%s pgrep %v", Specifications[i].Name, err)
		} else {
			switch Specifications[i].Category {
			case 0:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeWebServer, Specifications[i].Name)
			case 1:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeAppServer, Specifications[i].Name)
			case 2:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeQueue, Specifications[i].Name)
			case 3:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeCache, Specifications[i].Name)
			case 4:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeRDBMS, Specifications[i].Name)
			case 5:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeBigTable, Specifications[i].Name)
			case 6:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeKVStore, Specifications[i].Name)
			case 7:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeDOCStore, Specifications[i].Name)
			case 8:
				err = updatePI(&pids, &procinfolist, &procinfo, TypeGraphStore, Specifications[i].Name)
			}

			if err != nil {
				log.Printf("%s updatePI %%v", Specifications[i].Name, err)
			}
		}
	}

	// Node.JS
	/*
		for _, v := range procinfolist {
			log.Println(v)
		}
	*/
	return procinfolist, err
}

// GetProcessList ...
func GetProcessList(Specifications []Specification) []ProcInfoSnapshot {
	var piList []ProcInfoSnapshot
	piList, _ = GetProcessSnapshot(Specifications)

	return piList
}

// GetPidList ...
func GetPidList(piList []ProcInfoSnapshot) []int32 {
	var pids []int32

	for _, v := range piList {
		pids = append(pids, int32(v.Pid))
	}

	return pids
}

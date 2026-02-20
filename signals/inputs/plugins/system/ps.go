package system

import (
	gonet "net"
	"os"
	"strings"

	"opspect/config"
	"opspect/signals/inputs/plugins"

	dc "github.com/fsouza/go-dockerclient"
	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/docker"
	"github.com/shirou/gopsutil/v4/mem"
	"github.com/shirou/gopsutil/v4/net"
)

type DockerContainerStat struct {
	Id      string
	Name    string
	Command string
	Labels  map[string]string
	CPU     *docker.CgroupCPUStat
	Mem     *docker.CgroupMemStat
}

type PS interface {
	CPUTimes(perCPU, totalCPU bool) ([]cpu.TimesStat, error)
	DiskUsage() ([]*disk.UsageStat, error)
	NetIO() ([]net.IOCountersStat, error)
	NetProto() ([]net.ProtoCountersStat, error)
	DiskIO() (map[string]disk.IOCountersStat, error)
	VMStat() (*mem.VirtualMemoryStat, error)
	SwapStat() (*mem.SwapMemoryStat, error)
	DockerStat() ([]*DockerContainerStat, error)
	NetConnections() ([]net.ConnectionStat, error)
}

func add(acc plugins.Accumulator,
	name string, val float64, tags map[string]string) {
	if val >= 0 {
		acc.Add(name, val, tags)
	}
}

type systemPS struct {
	dockerClient *dc.Client
}

func (s *systemPS) CPUTimes(perCPU, totalCPU bool) ([]cpu.TimesStat, error) {
	var cpuTimes []cpu.TimesStat
	if perCPU {
		if perCPUTimes, err := cpu.Times(true); err == nil {
			cpuTimes = append(cpuTimes, perCPUTimes...)
		} else {
			return nil, err
		}
	}
	if totalCPU {
		if totalCPUTimes, err := cpu.Times(false); err == nil {
			cpuTimes = append(cpuTimes, totalCPUTimes...)
		} else {
			return nil, err
		}
	}
	return cpuTimes, nil
}

func (s *systemPS) DiskUsage() ([]*disk.UsageStat, error) {
	parts, err := disk.Partitions(true)
	if err != nil {
		return nil, err
	}

	var usage []*disk.UsageStat

	for _, p := range parts {
		if _, err := os.Stat(p.Mountpoint); err == nil {
			du, err := disk.Usage(p.Mountpoint)
			if err != nil {
				return nil, err
			}
			du.Fstype = p.Fstype
			usage = append(usage, du)
		}
	}

	return usage, nil
}

func (s *systemPS) NetProto() ([]net.ProtoCountersStat, error) {
	return net.ProtoCounters(nil)
}

func (s *systemPS) NetIO() ([]net.IOCountersStat, error) {
	return net.IOCounters(true)
}

func (s *systemPS) NetConnections() ([]net.ConnectionStat, error) {
	return net.Connections("all")
}

func (s *systemPS) DiskIO() (map[string]disk.IOCountersStat, error) {
	m, err := disk.IOCounters()
	if err == config.NotImplementedError {
		return nil, nil
	}

	return m, err
}

func (s *systemPS) VMStat() (*mem.VirtualMemoryStat, error) {
	return mem.VirtualMemory()
}

func (s *systemPS) SwapStat() (*mem.SwapMemoryStat, error) {
	return mem.SwapMemory()
}

func (s *systemPS) DockerStat() ([]*DockerContainerStat, error) {
	if s.dockerClient == nil {
		c, err := dc.NewClient("unix:///var/run/docker.sock")
		if err != nil {
			return nil, err
		}

		s.dockerClient = c
	}

	opts := dc.ListContainersOptions{}

	containers, err := s.dockerClient.ListContainers(opts)
	if err != nil {
		if _, ok := err.(*gonet.OpError); ok {
			return nil, nil
		}

		return nil, err
	}

	var stats []*DockerContainerStat

	for _, container := range containers {
		ctu, err := docker.CgroupCPUDocker(container.ID)
		if err != nil {
			return nil, err
		}

		mem, err := docker.CgroupMemDocker(container.ID)
		if err != nil {
			return nil, err
		}

		name := strings.Join(container.Names, " ")

		stats = append(stats, &DockerContainerStat{
			Id:      container.ID,
			Name:    name,
			Command: container.Command,
			Labels:  container.Labels,
			CPU:     ctu,
			Mem:     mem,
		})
	}

	return stats, nil
}

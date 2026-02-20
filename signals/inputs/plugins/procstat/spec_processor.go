package procstat

import (
	"fmt"
	"io/ioutil"
	"log"
	"strconv"

	"github.com/shirou/gopsutil/v4/process"
	"opspect/signals/inputs/plugins"
	"opspect/util/discovery"
)

// SpecProcessor ...
type SpecProcessor struct {
	Prefix string
	tags   map[string]string
	acc    plugins.Accumulator
	proc   *process.Process
	pi     discovery.ProcInfoSnapshot
}

func (p *SpecProcessor) add(metric string, value interface{}) {
	var mname string
	if p.Prefix == "" {
		mname = metric
	} else {
		mname = p.Prefix + "_" + metric
	}
	p.acc.Add(mname, value, p.tags)
}

// GetProcessInfo ...
func GetProcessInfo(acc plugins.Accumulator, Specifications []Specification) bool {
	// Discover fresh process list
	var piList []discovery.ProcInfoSnapshot
	var dSpecs = make([]discovery.Specification, len(Specifications))

	for i := 0; i < len(Specifications); i++ {
		dSpecs[i].PidFile = Specifications[i].PidFile
		dSpecs[i].Exe = Specifications[i].Exe
		dSpecs[i].Prefix = Specifications[i].Prefix
		dSpecs[i].Pattern = Specifications[i].Pattern
		dSpecs[i].Category = Specifications[i].Category
		dSpecs[i].Name = Specifications[i].Name
	}

	piList = discovery.GetProcessList(dSpecs)
	for _, pi := range piList {
		var Prefix string
		p := NewSpecProcessor(Prefix, acc, pi)
		p.pushMetrics()
	}
	return true
}

// NewSpecProcessor ...
func NewSpecProcessor(prefix string, acc plugins.Accumulator, pi discovery.ProcInfoSnapshot) *SpecProcessor {
	p, _ := process.NewProcess(int32(pi.Pid))
	tags := make(map[string]string)
	tags["pid"] = fmt.Sprintf("%v", p.Pid)
	if name, err := p.Name(); err == nil {
		tags["name"] = name
	}

	return &SpecProcessor{
		Prefix: prefix,
		tags:   tags,
		acc:    acc,
		proc:   p,
		pi:     pi,
	}
}

func (p *SpecProcessor) pushMetrics() {
	if err := p.pushNWListenPorts(); err != nil {
		log.Printf("procstat, nw listening ports not available: %s", err.Error())
	}

	if err := p.pushCmdLine(); err != nil {
		log.Printf("procstat, fd stats not available: %s", err.Error())
	}
	if err := p.pushFDStats(); err != nil {
		log.Printf("procstat, fd stats not available: %s", err.Error())
	}
	if err := p.pushCtxStats(); err != nil {
		log.Printf("procstat, ctx stats not available: %s", err.Error())
	}
	if err := p.pushIOStats(); err != nil {
		log.Printf("procstat, io stats not available: %s", err.Error())
	}
	if err := p.pushCPUStats(); err != nil {
		log.Printf("procstat, cpu stats not available: %s", err.Error())
	}
	if err := p.pushMemoryStats(); err != nil {
		log.Printf("procstat, mem stats not available: %s", err.Error())
	}
}

func (p *SpecProcessor) pushCmdLine() error {

	// asit
	// todo: Add env processing for windows
	// todo: Add support for one time publish for specific points

	filename := "/proc/" + strconv.Itoa(int(p.proc.Pid)) + "/cmdline"
	contents, err := ioutil.ReadFile(filename)
	if err != nil {
		log.Fatalf("procstat, unable to read stats %s", err.Error())
	}
	s := string(contents[:len(contents)-1])
	p.add("cmd", s)

	return err
}

func (p *SpecProcessor) pushFDStats() error {
	fds, err := p.proc.NumFDs()
	if err != nil {
		return fmt.Errorf("NumFD error: %s\n", err)
	}
	p.add("num_fds", fds)
	return nil
}

func (p *SpecProcessor) pushCtxStats() error {
	ctx, err := p.proc.NumCtxSwitches()
	if err != nil {
		return fmt.Errorf("ContextSwitch error: %s\n", err)
	}
	p.add("voluntary_context_switches", ctx.Voluntary)
	p.add("involuntary_context_switches", ctx.Involuntary)
	return nil
}

func (p *SpecProcessor) pushIOStats() error {
	io, err := p.proc.IOCounters()
	if err != nil {
		return fmt.Errorf("IOCounters error: %s\n", err)
	}
	p.add("read_count", io.ReadCount)
	p.add("write_count", io.WriteCount)
	p.add("read_bytes", io.ReadBytes)
	p.add("write_bytes", io.WriteCount)
	return nil
}

func (p *SpecProcessor) pushCPUStats() error {
	cpu, err := p.proc.Times()
	if err != nil {
		return err
	}
	p.add("cpu_user", cpu.User)
	p.add("cpu_system", cpu.System)
	p.add("cpu_idle", cpu.Idle)
	p.add("cpu_nice", cpu.Nice)
	p.add("cpu_iowait", cpu.Iowait)
	p.add("cpu_irq", cpu.Irq)
	p.add("cpu_soft_irq", cpu.Softirq)
	p.add("cpu_steal", cpu.Steal)
	p.add("cpu_guest", cpu.Guest)
	p.add("cpu_guest_nice", cpu.GuestNice)
	return nil
}

func (p *SpecProcessor) pushMemoryStats() error {
	mem, err := p.proc.MemoryInfo()
	if err != nil {
		return err
	}
	p.add("memory_rss", mem.RSS)
	p.add("memory_vms", mem.VMS)
	p.add("memory_swap", mem.Swap)
	return nil
}

func (p *SpecProcessor) pushNWListenPorts() error {
	for _, pi := range p.pi.Portinfolist {
		p.tags["svc_name"] = fmt.Sprintf("%s:%d:%d:%d:%d:%s",
			p.pi.Name, int(p.pi.Category), int(pi.Ipver), int(pi.Proto), int(pi.Port), pi.Ip)
		p.add("service_type", int(p.pi.Category))
		p.add("service_name", p.pi.Name)
		p.add("ipver", int(pi.Ipver))
		p.add("proto", int(pi.Proto))
		p.add("port", int(pi.Port))
		p.add("interface", pi.Ip)
	}
	p.tags["svc_name"] = p.pi.Name
	return nil
}

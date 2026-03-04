package procstat

import (
	"io/ioutil"
	"os"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"opscog/util/test"
)

func TestGather(t *testing.T) {
	var acc test.Accumulator
	pid := os.Getpid()
	file, err := ioutil.TempFile(os.TempDir(), "agent")
	require.NoError(t, err)
	file.Write([]byte(strconv.Itoa(pid)))
	file.Close()
	defer os.Remove(file.Name())
	specifications := []Specification{{PidFile: file.Name(), Prefix: "foo"}}
	p := Procstat{
		Specifications: specifications,
	}
	p.Gather(&acc)
	assert.True(t, acc.HasFloatValue("foo_cpu_user"))
	assert.True(t, acc.HasUIntValue("foo_memory_vms"))
}

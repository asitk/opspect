package circularbuffer

import (
	"log"
	// "time"
)

type circularbuffer struct {
	size         int
	in_use       bool
	write_offset int
	read_offset  int
	buffer       []byte
	_lock        chan bool
}

var hmap map[string]circularbuffer

func Init(hkey string, maxsize int) bool {
	if len(hmap) == 0 {
		hmap = make(map[string]circularbuffer)
	}
	_, ok := hmap[hkey]
	if !ok {
		var cb circularbuffer
		cb.size = maxsize
		cb.buffer = make([]byte, maxsize)
		cb._lock = make(chan bool, 1)
		log.Println("Initializing key=", hkey, "  buffer size to ", maxsize)
		hmap[hkey] = cb
		return true
	}
	return false
}

func Shutdown(hkey string) {
	if len(hmap) > 0 {
		_, ok := hmap[hkey]
		if ok && lock(hkey) {
			log.Println("Shutting down key=", hkey)
			delete(hmap, hkey)
		}
	}
}

func lock(hkey string) bool {
	if len(hmap) == 0 {
		return false
	}
	cb := hmap[hkey]
	cb._lock <- true

	//log.Println("Acquired lock")
	cb.in_use = true
	hmap[hkey] = cb
	return true
}

func unlock(hkey string) bool {
	if len(hmap) == 0 {
		return false
	}

	cb := hmap[hkey]
	if cb.in_use {
		cb.in_use = false
		<-cb._lock
		hmap[hkey] = cb
		//log.Println("Release lock")
		return true
	}

	return false
}
func Read(hkey string, buf []byte) (int, int) {
	var err int = -1
	if len(hmap) == 0 {
		return 0, 2
	}
	if !lock(hkey) {
		return 0, 2
	}

	cb := hmap[hkey]
	need := len(buf)
	log.Println("Before READ hkey = ", hkey, "max need = ", need, "  read_offset = ", cb.read_offset, " write_offset = ", cb.write_offset)

	available := 0
	if cb.read_offset <= cb.write_offset {
		available = cb.write_offset - cb.read_offset
	} else {
		available = cb.size - cb.read_offset + cb.write_offset
	}

	if need > available {
		need = available
	}

	if need == 0 {
		unlock(hkey)
		return need, err
	} else {
		err = 0
	}

	off := cb.write_offset - cb.read_offset
	if off < 0 { //This is the n+1 write over n reads
		a := cb.size - cb.read_offset
		b := cb.write_offset
		if need <= (a + b) {
			if need <= a {
				copy(buf[:need], cb.buffer[cb.read_offset:])
				cb.read_offset += need
			} else {
				copy(buf, cb.buffer[cb.read_offset:])
				copy(buf[a:], cb.buffer[:need-a])
				cb.read_offset = need - a
			}
		}
	} else {
		if need <= off {
			copy(buf, cb.buffer[cb.read_offset:cb.read_offset+need])
			cb.read_offset += need
		}
	}
	hmap[hkey] = cb
	unlock(hkey)
	log.Println("After READ hkey = ", hkey, "available = ", available, "  read_offset = ", cb.read_offset, " write_offset = ", cb.write_offset)

	return need, err
}

func Write(hkey string, pkt []byte) (int, int) {
	log.Println("In func Write")
	var err int = 0
	if len(hmap) == 0 {
		return 0, 2
	}
	if !lock(hkey) {
		return 0, 2
	}
	n := len(pkt)
	cb := hmap[hkey]

	available := 0
	if cb.write_offset < cb.read_offset {
		available = cb.read_offset - cb.write_offset
	} else {
		available = cb.size - cb.write_offset + cb.read_offset - 1
	}

	if n > available {
		n = available
	} else {
		err = 0
	}
	log.Println("Before WRITE hkey = ", hkey, "available =", available, "n = ", n, "size = ", cb.size, " read_offset = ", cb.read_offset, " write_offset = ", cb.write_offset)
	if n == 0 {
		unlock(hkey)
		return n, err
	}

	off := cb.read_offset - cb.write_offset
	if off > 0 {
		if n <= off {
			copy(cb.buffer[cb.write_offset:], pkt[:n])
			cb.write_offset += n
		}
	} else {
		a := cb.size - cb.write_offset
		b := cb.read_offset - 1
		if n <= (a + b) {
			if n <= a {
				copy(cb.buffer[cb.write_offset:], pkt[:n])
				cb.write_offset += n
			} else {
				copy(cb.buffer[cb.write_offset:], pkt[:a])
				copy(cb.buffer[:n-a], pkt[a:])
				cb.write_offset = n - a
			}
		}
	}
	log.Println("After WRITE hkey = ", hkey, "available =", available, "n = ", n, "size = ", cb.size, " read_offset = ", cb.read_offset, " write_offset = ", cb.write_offset)

	hmap[hkey] = cb
	unlock(hkey)
	//   log.Println("In write_cb after Got ", n, "bytes = ", cb.buffer)
	return n, err
}

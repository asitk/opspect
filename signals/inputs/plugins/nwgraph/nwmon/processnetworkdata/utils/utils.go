//The package implements processing data and creating stats for it
package nwamutils
import (
    "fmt"
    "log"
)

var debug bool = false
func DebugPrint(format string, args ...interface{}) {
    if debug {
        log.Println(fmt.Sprintf(format, args...))
    }

}

func Convert_to_string(byteArr [] byte, n int) string { 
    ba := ""
    /* Assume you are dealing with ASCII only */
    for i:= 0; i < n; i++ {
        if byteArr[i] > 31 && byteArr[i] < 128 {
            ba += string(byteArr[i])
        }
    }
    return ba
}

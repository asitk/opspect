package file

import (
	"os"

	log "github.com/Sirupsen/logrus"

	"bitbucket.org/infrared/models/client"
	"bitbucket.org/infrared/signals/outputs"
)

type File struct {
	Filename string
	FHandle  *os.File
}

var sampleConfig = `logfile = "/tmp/metrics.log"`
var of File

func (k *File) Connect() error {
	var err error
	of.Filename = "/tmp/metrics.log"

	log.Println("Opening Log /tmp/metrics.log")
	of.FHandle, err = os.OpenFile(of.Filename, os.O_APPEND|os.O_WRONLY|os.O_CREATE, 0644)
	if err != nil {
		log.Printf("Unable to open file \n%s\n", err.Error())
		return err
	}
	return nil
}

func (k *File) Close() error {
	log.Println("Closing Log")
	of.FHandle.Sync()
	return of.FHandle.Close()
}

func (k *File) SampleConfig() string {
	return sampleConfig
}

func (k *File) Description() string {
	return "Configuration for the File server to send metrics to"
}

func (k *File) Write(points []*client.Point) error {

	if len(points) == 0 {
		return nil
	}

	for _, p := range points {
		value := p.String()
		of.FHandle.WriteString(value + "\n")

		// asit debug
		log.Printf("%s", value)
	}
	return nil
}

func init() {
	outputs.Add("file", func() outputs.Output {
		return &File{}
	})
}

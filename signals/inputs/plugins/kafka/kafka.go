package kafka

import (
	"fmt"
	"github.com/IBM/sarama"
	"opspect/signals/inputs/plugins"
	"time"
)

type Kafka struct {
	// Kafka brokers to send metrics to
	Brokers []string `toml:"brokers"`
	// Kafka topic
	Topic string `toml:"topic"`
	// Routing Key Tag
	RoutingTag string `toml:"routing_tag"`

	consumer sarama.Consumer
}

var sampleConfig = `
  # URLs of kafka brokers
  brokers = ["localhost:9092"]
  # Kafka topic for producer messages
  topic = "meta"
  # tag to use as a routing key
  # ie, if this tag exists, it's value will be used as the routing key
  routing_tag = "host"
`

func (k *Kafka) Connect() error {
	consumer, err := sarama.NewConsumer(k.Brokers, nil)
	if err != nil {
		fmt.Printf("Failed to connect to kafka because %v", err)
		return err
	}
	k.consumer = consumer
	return nil
}

var message []byte

func (k *Kafka) Close() error {
	return k.consumer.Close()
}

func (k *Kafka) SampleConfig() string {
	return sampleConfig
}

func (k *Kafka) Description() string {
	return "Configuration for the Kafka server to send meta from"
}

func (k *Kafka) ReadFromConsumer(i int64) (int64, error) {
	var partitionConsumer sarama.PartitionConsumer
	var partitions []int32
	var err error

	partitions, err = k.consumer.Partitions(k.Topic)
	//fmt.Printf("Reading from consumer 1 with offset = %d\n", i)
	if err == nil {
		part_len := len(partitions)
		//fmt.Printf("Reading from consumer 2 with num partitions = %d\n", part_len)

		err = sarama.ErrOffsetOutOfRange
		if part_len > 0 {
			partitionConsumer, err = k.consumer.ConsumePartition(k.Topic, partitions[part_len-1], i)
		}
		if err == nil {
			//fmt.Printf("Reading from consumer 3 \n")
			isBlocked <- true
			m := <-partitionConsumer.Messages()
			message = make([]byte, len(m.Value))
			copy(message, m.Value)
			hasNewData = true
			//fmt.Printf("A Got new message = %s\n", message)
			isBlocked <- false
		} else {
			fmt.Printf("Got error: %v\n", err)
		}
	} else {
		fmt.Printf("Got error: %v\n", err)
	}
	return i, err
}

var isBlocked = make(chan bool)
var hasNewData bool

var bailout bool = false

func (k *Kafka) ProcessMessages() {
	var i int64 = 0
	var err error
	go k.notifyChannels()
	for !bailout {
		k.Connect()
		i, err = k.ReadFromConsumer(i)
		if err != nil {
			i = 0
			time.Sleep(5000000000)
		} else {
			i++
		}
		k.Close()
	}

}

var notifyChannelMap = make(map[chan bool]chan bool)

func SubscribeForNotification(nChannel *chan bool) {
	notifyChannelMap[*nChannel] = *nChannel
}

func UnsubscribeForNotification(nChannel *chan bool) {
	delete(notifyChannelMap, *nChannel)
}

func (k *Kafka) notifyChannels() {
	var prev_message string = ""
	for !bailout {
		blockValue := <-isBlocked
		//fmt.Printf("B Got unblocked %v\n", blockValue)
		if blockValue == false && hasNewData == true {
			if prev_message != string(message) {
				prev_message = string(message)
				fmt.Printf("Notifying plugins....\n")
				for k, _ := range notifyChannelMap {
					k <- true
				}
			}
			hasNewData = false
		}
	}
}

func Read() string {
	if message == nil {
		message = make([]byte, 1)
		message[0] = 0
	}
	//fmt.Printf("Message is %s\n", string(message))
	return string(message)
}

func NewKafka() *Kafka {
	k := &Kafka{}
	go k.ProcessMessages()

	return k
}

func init() {
	plugins.Add("kafka", func() plugins.Plugin {
		return NewKafka()
	})
}

func (p *Kafka) GatherUnmanagedAsync(shutdown chan struct{}) error {
	return nil
}

// Gather ...
func (p *Kafka) Gather(acc plugins.Accumulator) error {
	//init()
	return nil
}

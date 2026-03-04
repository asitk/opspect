package kafka

import (
	"testing"

	"github.com/stretchr/testify/require"
	"opscog/util/test"
)

func TestConnectAndWrite(t *testing.T) {
	if testing.Short() {
		t.Skip("Skipping integration test in short mode")
	}

	brokers := []string{test.GetLocalHost() + ":9092"}
	k := &Kafka{
		Brokers: brokers,
		Topic:   "Test",
	}

	// Verify that we can connect to the Kafka broker
	err := k.Connect()
	require.NoError(t, err)

	// Verify that we can successfully write data to the kafka broker
	// err = k.Write(testutil.MockBatchPoints().Points())
	// require.NoError(t, err)
}

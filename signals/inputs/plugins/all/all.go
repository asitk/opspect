package all

// importing the plugins
import (
	_ "bitbucket.org/infrared/signals/inputs/plugins/logs"
	_ "bitbucket.org/infrared/signals/inputs/plugins/kafka"
	_ "bitbucket.org/infrared/signals/inputs/plugins/memcached"
	_ "bitbucket.org/infrared/signals/inputs/plugins/nwgraph"
	_ "bitbucket.org/infrared/signals/inputs/plugins/procstat"
	_ "bitbucket.org/infrared/signals/inputs/plugins/system"
)

.PHONY: build test clean lint

CGO_CFLAGS=-I/home/linuxbrew/.linuxbrew/opt/libpcap/include
CGO_LDFLAGS=-L/home/linuxbrew/.linuxbrew/opt/libpcap/lib

build:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go build ./...

test:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go test ./...

clean:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go clean

lint:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go vet ./...

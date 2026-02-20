.PHONY: build test clean lint format format-go format-python format-scala format-java

CGO_CFLAGS=-I/home/linuxbrew/.linuxbrew/opt/libpcap/include
CGO_LDFLAGS=-L/home/linuxbrew/.linuxbrew/opt/libpcap/lib
SCALAFMT=/home/asitk/.local/share/coursier/bin/scalafmt
GOOGLE_JAVA_FORMAT=/home/asitk/.local/share/google-java-format/google-java-format

build:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go build ./...

test:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go test ./...

clean:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go clean

lint:
	CGO_CFLAGS=$(CGO_CFLAGS) CGO_LDFLAGS=$(CGO_LDFLAGS) go vet ./...

format: format-go format-python format-scala format-java

format-go:
	go fmt ./...

format-python:
	@which black >/dev/null 2>&1 && black . || echo "Install black: pip install black"

format-scala:
	@if [ -f "$(SCALAFMT)" ]; then \
		$(SCALAFMT) models/kafka/consumer/src/main/java/; \
	else \
		echo "Install scalafmt: cs install scalafmt"; \
	fi

format-java:
	@if [ -f "$(GOOGLE_JAVA_FORMAT)" ]; then \
		find models/kafka/consumer/src/main/java models/kafka/consumer/src/test/java apps -name "*.java" -type f 2>/dev/null | xargs -I {} $(GOOGLE_JAVA_FORMAT) --replace {} 2>/dev/null || true; \
		echo "Java formatting complete"; \
	else \
		echo "Install google-java-format: download from GitHub"; \
	fi

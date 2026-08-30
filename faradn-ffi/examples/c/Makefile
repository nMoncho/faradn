# Build the C example against the shared library produced by
#   mvn -Pnative-shared -pl faradn-ffi -am -DskipTests package
#
#   make            # compile ./render
#   make run        # render to stdout (redirect or pipe to a printer)
#   make clean

TARGET  := ../../target
CFLAGS  += -I$(TARGET)
LDFLAGS += -L$(TARGET) -lfaradn

# Bake the library path into the binary so it runs without setting
# DYLD_LIBRARY_PATH / LD_LIBRARY_PATH by hand.
UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
  LDFLAGS += -Wl,-rpath,$(TARGET)
else
  LDFLAGS += -Wl,-rpath,$(TARGET)
endif

render: render.c
	$(CC) $(CFLAGS) render.c $(LDFLAGS) -o render

.PHONY: run clean
run: render
	./render

clean:
	rm -f render

CC-RISCV = riscv64-unknown-elf-gcc

RISCV-TESTS = ../riscv-tests
CFLAGS-RISCV = -DPREALLOCATE=1 -mcmodel=medany -static -std=gnu99 -O2 -ffast-math -fno-common -fno-builtin-printf -fno-tree-loop-distribute-patterns -Wno-implicit -march=rv32imac -mabi=ilp32
CINCLUDE = -I$(RISCV-TESTS)/env -I$(RISCV-TESTS)/benchmarks/common
CLINK = $(RISCV-TESTS)/benchmarks/common/syscalls.c $(RISCV-TESTS)/benchmarks/common/crt.S -static -nostdlib -nostartfiles -lm -lgcc -T $(RISCV-TESTS)/benchmarks/common/test.ld

DHRY_SRCS := dhrystone_main.c dhrystone.c
DHRY_CFLAGS := -ffreestanding -DTIME -fno-inline -Wno-implicit -mexplicit-relocs

default: dhrystone

clean:
	rm -f *.o *.riscv

dhrystone: dhrystone_main.c dhrystone.c
	$(CC-RISCV) $(CINCLUDE) $(CFLAGS-RISCV) -o dhrystone.riscv $(DHRY_SRCS) $(CLINK)
.PHONY: all

all: clean
	mvn -fn compile > Out.txt 2>&1
	grep -qF "MavenExample.java:[29,29] error: [assignment.type.incompatible] incompatible types in assignment." Out.txt

clean:
	mvn -q clean
	rm -f Out.txt

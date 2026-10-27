#!/bin/bash

# javadoc -sourcepath ../ -subpackages core:ui:gui:input:movement:report:routing:applications:interfaces

# javadoc -d doc -sourcepath src $(find src -name "*.java")
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
javadoc -d "$SCRIPT_DIR" -sourcepath "$SCRIPT_DIR/../src" -subpackages .
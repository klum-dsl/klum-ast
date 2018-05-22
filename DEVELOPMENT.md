Dump the generated AST tree:

in a groovy Console (in intellij Ctrl-Shift-A: groovyConsole)

```groovy
import groovy.inspect.swingui.AstNodeToScriptAdapter
import org.codehaus.groovy.control.CompilePhase

def script = """
import com.blackbuild.groovy.configdsl.transform.*

// code

"""

new AstNodeToScriptAdapter().compileToScript(script, CompilePhase.CLASS_GENERATION.phaseNumber)
```


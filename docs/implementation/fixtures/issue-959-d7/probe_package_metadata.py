#
# The MIT License (MIT)
#
# Copyright (c) 2015-2026 Stephan Pauxberger
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

"""Issue #959 D7: package-info retention probe using only existing @Deprecated.

Arguments: JDK home, then one Groovy compiler JAR per lane (3, 4, 5).
No new annotation or Klum API is defined. Temporary producer sources are deleted
before a separately compiled Java binary consumer inspects package metadata.
"""
import pathlib
import shutil
import subprocess
import sys
import tempfile

jdk = pathlib.Path(sys.argv[1]) / "bin"


def run(*args):
    return subprocess.run([str(a) for a in args], check=True, capture_output=True, text=True).stdout.strip()


with tempfile.TemporaryDirectory(prefix="klum-d7-package-") as temporary:
    root = pathlib.Path(temporary)
    for index, groovy in enumerate([None] + sys.argv[2:]):
        lane = "java" if groovy is None else pathlib.Path(groovy).stem
        producer = root / str(index)
        source = producer / "src"
        classes = producer / "classes"
        source.mkdir(parents=True)
        classes.mkdir()
        extension = "java" if groovy is None else "groovy"
        (source / ("package-info." + extension)).write_text("@Deprecated\npackage family;\n")
        (source / "Member.java").write_text("package family; public class Member { static { if (true) throw new AssertionError(\"initialized\"); } }")
        (source / "Sub.java").write_text("package family.child; public class Sub {}")
        if groovy:
            run(jdk / "java", "-cp", groovy, "org.codehaus.groovy.tools.FileSystemCompiler",
                "-d", classes, source / ("package-info." + extension))
        else:
            run(jdk / "javac", "-d", classes, source / "package-info.java")
        run(jdk / "javac", "-d", classes, source / "Member.java", source / "Sub.java")
        assert (classes / "family/package-info.class").exists(), lane
        archive = producer / "family.jar"
        run(jdk / "jar", "cf", archive, "-C", classes, ".")
        shutil.rmtree(source)
        shutil.rmtree(classes)
        consumer = producer / "Consumer.java"
        consumer.write_text('''
            public class Consumer {
                public static void main(String[] args) throws Exception {
                    ClassLoader loader = Consumer.class.getClassLoader();
                    Class<?> member = Class.forName("family.Member", false, loader);
                    Class<?> child = Class.forName("family.child.Sub", false, loader);
                    if (!member.getPackage().isAnnotationPresent(Deprecated.class)) throw new AssertionError("missing package fact");
                    if (child.getPackage().isAnnotationPresent(Deprecated.class)) throw new AssertionError("package inherited");
                    try {
                        Class.forName("family.Member", true, loader);
                        throw new IllegalStateException("initialization trap was not active");
                    } catch (AssertionError expected) {
                        if (!"initialized".equals(expected.getMessage())) throw expected;
                    }
                    System.out.println("package fact retained; subpackage not inherited; initialization control passed");
                }
            }
        ''')
        output = producer / "consumer"
        output.mkdir()
        run(jdk / "javac", "-d", output, consumer)
        consumer.unlink()
        print(lane + ": " + run(jdk / "java", "-cp", str(output) + ":" + str(archive), "Consumer"))

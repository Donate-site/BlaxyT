import java.nio.file.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.writer.pool.*;

public final class MergeSettings {
 public static void main(String[] args) throws Exception {
  var patch = DexFileFactory.loadDexFile(Path.of(args[1]).toFile(), Opcodes.getDefault());
  var replacements = new HashMap<String,ClassDef>();
  for (var c : patch.getClasses()) replacements.put(c.getType(), c);
  var original = DexFileFactory.loadDexFile(Path.of(args[0]).toFile(), Opcodes.getDefault());
  var classes = new ArrayList<ClassDef>();
  for (var old : original.getClasses()) {
   var c = replacements.getOrDefault(old.getType(), old);
   var direct = new ArrayList<Method>(); var virtual = new ArrayList<Method>();
   for (var m : c.getDirectMethods()) direct.add(CanonicalizeDex.canonical(m));
   for (var m : c.getVirtualMethods()) virtual.add(CanonicalizeDex.canonical(m));
   classes.add(new ImmutableClassDef(c.getType(), c.getAccessFlags(), c.getSuperclass(), c.getInterfaces(), c.getSourceFile(), c.getAnnotations(), c.getStaticFields(), c.getInstanceFields(), direct, virtual));
  }
  DexPool.writeTo(args[2], new ImmutableDexFile(original.getOpcodes(), classes));
 }
}

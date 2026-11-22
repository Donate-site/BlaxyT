import java.nio.file.*;import java.util.*;import org.jf.dexlib2.*;import org.jf.dexlib2.iface.*;import org.jf.dexlib2.iface.instruction.*;import org.jf.dexlib2.builder.*;import org.jf.dexlib2.builder.instruction.*;import org.jf.dexlib2.immutable.*;import org.jf.dexlib2.writer.pool.*;
/** Rebuild sorted DEX pools after package-string edits; recalculate branches for jumbo strings. */
public class CanonicalizeDex {
 static Method canonical(Method m){
  var body=m.getImplementation();if(body==null)return m;
  var out=new MutableMethodImplementation(body);int index=0;
  for(var instruction:body.getInstructions()){
   if(instruction.getOpcode()==Opcode.CONST_STRING){var ref=(ReferenceInstruction)instruction;int reg=((OneRegisterInstruction)instruction).getRegisterA();out.replaceInstruction(index,new BuilderInstruction31c(Opcode.CONST_STRING_JUMBO,reg,ref.getReference()));}
   index++;
  }
  return new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),new ImmutableMethodImplementation(out.getRegisterCount(),out.getInstructions(),out.getTryBlocks(),List.of()));
 }
 public static void main(String[]args)throws Exception{
  Path dir=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);
  List<Path> files;try(var paths=Files.list(dir)){files=paths.filter(p->p.toString().endsWith(".dex")).sorted().toList();}
  for(Path file:files){
   var dex=DexFileFactory.loadDexFile(file.toFile(),Opcodes.getDefault());var classes=new ArrayList<ClassDef>();
   for(var c:dex.getClasses()){
    List<Method> direct=new ArrayList<>(),virtual=new ArrayList<>();for(var m:c.getDirectMethods())direct.add(canonical(m));for(var m:c.getVirtualMethods())virtual.add(canonical(m));
    classes.add(new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getStaticFields(),c.getInstanceFields(),direct,virtual));
   }
   DexPool.writeTo(output.resolve(file.getFileName()).toString(),new ImmutableDexFile(dex.getOpcodes(),classes));
   System.out.println("Rebuilt sorted DEX pools: "+file.getFileName());classes=null;dex=null;System.gc();
  }
 }
}

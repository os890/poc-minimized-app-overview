// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.analysis;import java.io.*;import java.lang.annotation.Annotation;
import java.lang.reflect.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.security.CodeSource;import java.util.*;import java.util.jar.*;import org.os890.cdi.callrecorder.analysis.MetadataModel.*;
import org.os890.cdi.callrecorder.core.CallRecorder.*;public final class MetadataModel{private MetadataModel(){
}public enum Kind{CLASS,ABSTRACT_CLASS,INTERFACE,ENUM,ANNOTATION}public static final class TypeDecl{
public final Kind kind;public final String name;public final List<TypeParam>typeParameters=new ArrayList<TypeParam>();
public final List<TypeRef>supertypes=new ArrayList<TypeRef>();public final List<Meta>metas=new ArrayList<Meta>();
public final List<FieldDecl>fields=new ArrayList<FieldDecl>();public final List<MethodDecl>methods=new ArrayList<MethodDecl>();
public final List<String>constants=new ArrayList<String>();public TypeDecl(Kind kind,String name){this.kind=kind;
this.name=name;}public String simpleName(){return name.substring(name.lastIndexOf('.')+1);}public String syntheticPackage(){
return name.lastIndexOf('.')<0?"":name.substring(0,name.lastIndexOf('.'));}}public static final class TypeParam{
public final String name;public final List<TypeRef>bounds=new ArrayList<TypeRef>();public TypeParam(String name){
this.name=name;}}public static final class FieldDecl{public final String name;public final boolean property;
public final TypeRef type;public final List<Meta>metas=new ArrayList<Meta>();public FieldDecl(String name,boolean property,TypeRef type){
this.name=name;this.property=property;this.type=type;}}public static final class MethodDecl{public final String verb;
public final String path;public final String name;public final List<ParamDecl>parameters=new ArrayList<ParamDecl>();
public TypeRef returnType;public final List<Meta>metas=new ArrayList<Meta>();public MethodDecl(String verb,String path,String name){
this.verb=verb;this.path=path;this.name=name;}public boolean isConstructor(){return name==null;}}public static final class ParamDecl{
public final List<Meta>metas=new ArrayList<Meta>();public TypeRef type;}public static final class TypeRef{
public final List<AnnotationRef>annotations=new ArrayList<AnnotationRef>();public final String name;
public final List<TypeRef>arguments=new ArrayList<TypeRef>();public int dimensions;public String boundKind;
public TypeRef bound;public TypeRef(String name){this.name=name;}public boolean isWildcard(){return"?".equals(name);
}public TypeRef substitute(Map<String,TypeRef>variables){TypeRef replacement=variables.get(name);if(replacement!=null&&arguments.isEmpty()&&
annotations.isEmpty()){TypeRef result=replacement.copy();result.dimensions+=dimensions;return result;
}TypeRef result=new TypeRef(name);result.annotations.addAll(annotations);for(TypeRef argument:arguments){
result.arguments.add(argument.substitute(variables));}result.dimensions=dimensions;result.boundKind=boundKind;
result.bound=bound==null?null:bound.substitute(variables);return result;}TypeRef copy(){return substitute(Collections.<String,TypeRef>emptyMap());
}}public abstract static class Meta{}public static final class AnnotationRef extends Meta{public final String name;
public final Map<String,Value>members=new LinkedHashMap<String,Value>();public AnnotationRef(String name){
this.name=name;}}public static final class PathMeta extends Meta{public final String path;public PathMeta(String path){
this.path=path;}}public static final class MediaTypes extends Meta{public final boolean produces;public final List<String>types=new ArrayList<String>(
);public MediaTypes(boolean produces){this.produces=produces;}}public static final class ParamSource extends Meta{
public final char source;public final String name;public final String defaultValue;public ParamSource(char source,String name,String defaultValue){
this.source=source;this.name=name;this.defaultValue=defaultValue;}}public static final class Resolution extends Meta{
public final String targets;public Resolution(String targets){this.targets=targets;}}public static final class Value{
public enum Kind{STRING,CHAR,RAW,ANNOTATION,ARRAY}public final Kind kind;public final String text;public final AnnotationRef annotation;
public final List<Value>elements=new ArrayList<Value>();public Value(Kind kind,String text,AnnotationRef annotation){
this.kind=kind;this.text=text;this.annotation=annotation;}}public final static class ClassIndex{private static final Comparator<String>BY_SIMPLE_NAME=
new Comparator<String>(){public int compare(String a,String b){int result=simpleName(a).compareTo(simpleName(b));
return result!=0?result:a.compareTo(b);}};private final Map<String,List<String>>directSubtypes=new HashMap<String,List<String>>();
private final Map<String,File>markedClasses=new HashMap<String,File>();private final List<String>packagePrefixes;
private final Set<String>markerDescriptors=new HashSet<String>();private File currentEntry;private ClassIndex(List<String>packagePrefixes,Collection<
String>markerTypes){this.packagePrefixes=packagePrefixes;for(String markerType:markerTypes){markerDescriptors.add("L"+markerType.replace('.','/')+";")
;}}public static ClassIndex scan(Class<?>anchor,List<String>packagePrefixes){return scan(anchor,packagePrefixes,Collections.<String>emptyList());
}public static ClassIndex scan(Class<?>anchor,List<String>packagePrefixes,Collection<String>markerTypes){
ClassIndex index=new ClassIndex(packagePrefixes,markerTypes);for(File entry:classpathEntries(anchor)){
index.currentEntry=entry;try{if(entry.isDirectory()){index.scanDirectory(entry,entry);}else if(entry.isFile()){
index.scanJar(entry);}}catch(IOException e){}}return index;}public Set<String>markedClasses(){Set<String>result=new TreeSet<String>(BY_SIMPLE_NAME);
result.addAll(markedClasses.keySet());return result;}public File locationOf(String markedClassName){
return markedClasses.get(markedClassName);}public static File locationOf(Class<?>type){CodeSource codeSource=type.getProtectionDomain().getCodeSource(
);if(codeSource==null||codeSource.getLocation()==null){return null;}Deque<File>result=new ArrayDeque<File>();
addFile(codeSource.getLocation(),result);return result.isEmpty()?null:result.peek().getAbsoluteFile();
}public Set<String>subtypesOf(String className){Set<String>result=new TreeSet<String>(BY_SIMPLE_NAME);
Deque<String>pending=new ArrayDeque<String>();pending.add(className);while(!pending.isEmpty()){List<String>subtypes=directSubtypes.get(pending.poll())
;if(subtypes!=null){for(String subtype:subtypes){if(result.add(subtype)){pending.add(subtype);}}}}return result;
}private static Set<File>classpathEntries(Class<?>anchor){Set<File>result=new LinkedHashSet<File>();
Deque<File>pending=new ArrayDeque<File>();for(String entry:System.getProperty("java.class.path","").split(File.pathSeparator)){
if(!entry.isEmpty()){pending.add(new File(entry));}}CodeSource codeSource=anchor.getProtectionDomain().getCodeSource();
if(codeSource!=null&&codeSource.getLocation()!=null){addFile(codeSource.getLocation(),pending);}for(ClassLoader loader=anchor.getClassLoader();
loader!=null;loader=loader.getParent()){if(loader instanceof URLClassLoader){for(URL url:((URLClassLoader)loader).getURLs()){
addFile(url,pending);}}}while(!pending.isEmpty()){File entry=pending.poll().getAbsoluteFile();if(result.add(entry)&&entry.isFile()){
addManifestClassPath(entry,pending);}}return result;}private static void addManifestClassPath(File jar,Deque<File>pending){
try(JarFile jarFile=new JarFile(jar)){Manifest manifest=jarFile.getManifest();String classPath=manifest==null?null:manifest.getMainAttributes().
getValue(Attributes.Name.CLASS_PATH);if(classPath!=null){for(String entry:classPath.trim().split("\\s+")){
addFile(new URL(jar.toURI().toURL(),entry),pending);}}}catch(IOException e){}}private static void addFile(URL url,Deque<File>pending){
if("file".equals(url.getProtocol())){try{pending.add(new File(url.toURI()));}catch(URISyntaxException|IllegalArgumentException e){
pending.add(new File(url.getPath()));}}}private void scanDirectory(File root,File directory)throws IOException{
File[]children=directory.listFiles();if(children==null){return;}for(File child:children){if(child.isDirectory()){
scanDirectory(root,child);}else if(isIndexed(root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar,'/'))){
try(InputStream in=new FileInputStream(child)){index(in);}}}}private void scanJar(File file)throws IOException{
try(JarFile jar=new JarFile(file)){for(Enumeration<JarEntry>entries=jar.entries();entries.hasMoreElements();
){JarEntry entry=entries.nextElement();if(isIndexed(entry.getName())){try(InputStream in=jar.getInputStream(entry)){
index(in);}}}}}private boolean isIndexed(String path){if(!path.endsWith(".class")||path.startsWith("META-INF/")||path.endsWith("module-info.class")||
path.endsWith("package-info.class")){return false;}if(packagePrefixes.isEmpty()){return true;}String className=path.substring(0,path.length()-".class"
.length()).replace('/','.');for(String prefix:packagePrefixes){if(className.startsWith(prefix+".")){
return true;}}return false;}private void index(InputStream in)throws IOException{DataInputStream data=new DataInputStream(new BufferedInputStream(in))
;if(data.readInt()!=0xCAFEBABE){return;}data.readUnsignedShort();data.readUnsignedShort();int count=data.readUnsignedShort();
String[]utf8=new String[count];int[]classNameIndex=new int[count];for(int i=1;i<count;i++){int tag=data.readUnsignedByte();
switch(tag){case 1:utf8[i]=data.readUTF();break;case 7:classNameIndex[i]=data.readUnsignedShort();break;
case 8:case 16:case 19:case 20:data.readUnsignedShort();break;case 15:data.readUnsignedByte();data.readUnsignedShort();
break;case 3:case 4:case 9:case 10:case 11:case 12:case 17:case 18:data.readInt();break;case 5:case 6:data.readLong();
i++;break;default:return;}}data.readUnsignedShort();String className=className(utf8,classNameIndex,data.readUnsignedShort());
if(!markerDescriptors.isEmpty()){for(String entry:utf8){if(entry!=null&&markerDescriptors.contains(entry)){
markedClasses.put(className,currentEntry);break;}}}int superIndex=data.readUnsignedShort();if(superIndex!=0){
register(className(utf8,classNameIndex,superIndex),className);}int interfaces=data.readUnsignedShort();
for(int i=0;i<interfaces;i++){register(className(utf8,classNameIndex,data.readUnsignedShort()),className);
}}private void register(String superType,String subtype){List<String>subtypes=directSubtypes.get(superType);
if(subtypes==null){subtypes=new ArrayList<String>(2);directSubtypes.put(superType,subtypes);}subtypes.add(subtype);
}private static String simpleName(String className){return className.substring(Math.max(className.lastIndexOf('.'),className.lastIndexOf('$'))+1);
}private static String className(String[]utf8,int[]classNameIndex,int index){return utf8[classNameIndex[index]].replace('/','.');
}}public final static class MetadataReader{private final String text;private final boolean api;private int position;
private MetadataReader(String text,boolean api){this.text=text;this.api=api;}public static List<TypeDecl>readEntityMappings(String export){
return read(export,false);}public static List<TypeDecl>readApi(String export){return read(export,true);
}public static TypeRef parseType(String type){return new MetadataReader(type,false).completeType();}
private static List<TypeDecl>read(String export,boolean api){List<TypeDecl>result=new ArrayList<TypeDecl>();
int lineNumber=0;for(String line:export.split("\r?\n")){lineNumber++;if(line.trim().isEmpty()){continue;
}try{if(line.startsWith(" ")){if(result.isEmpty()){throw new IllegalArgumentException("member without type");
}readMember(line.substring(1),result.get(result.size()-1),api);}else{result.add(readType(line,api));
}}catch(RuntimeException e){throw new IllegalArgumentException("line "+lineNumber+": "+line+" - "+e.getMessage(),e);
}}return result;}private static TypeDecl readType(String line,boolean api){List<String>tokens=split(line,' ');
String head=tokens.get(0);TypeDecl result;if(head.startsWith("@")){result=new TypeDecl(Kind.ANNOTATION,head.substring(1));
}else if(head.endsWith("}")&&head.indexOf('{')>0){result=new TypeDecl(Kind.ENUM,head.substring(0,head.indexOf('{')));
String constants=head.substring(head.indexOf('{')+1,head.length()-1);for(String constant:split(constants,',')){
result.constants.add(constant.trim());}}else{Kind kind=head.startsWith("~")?Kind.ABSTRACT_CLASS:head.startsWith("^")?Kind.INTERFACE:Kind.CLASS;
MetadataReader reader=new MetadataReader(head,api);if(kind!=Kind.CLASS){reader.position++;}result=new TypeDecl(kind,reader.qualifiedName());
if(reader.peek('<')){reader.position++;do{reader.skipSpaces();TypeParam parameter=new TypeParam(reader.qualifiedName());
reader.skipSpaces();if(reader.text.startsWith("extends ",reader.position)){reader.position+="extends ".length();
parameter.bounds.add(reader.type());while(reader.peek('&')){reader.position++;parameter.bounds.add(reader.type());
}}result.typeParameters.add(parameter);}while(reader.accept(','));reader.expect('>');}if(reader.accept(':')){
do{result.supertypes.add(reader.type());}while(reader.accept(','));}reader.expectEnd();}for(int i=1;
i<tokens.size();i++){result.metas.add(meta(tokens.get(i),api));}return result;}private static void readMember(String line,TypeDecl type,boolean api){
List<String>tokens=split(line,' ');int signature=-1;if(api){for(int i=0;i<tokens.size()&&signature<0;
i++){if(tokens.get(i).matches("[A-Za-z_$][\\w$]*\\(.*")){signature=i;}}}if(signature<0){String name=tokens.get(0);
boolean property=!api&&name.endsWith("()");FieldDecl field=new FieldDecl(property?name.substring(0,name.length()-2):name,property,new MetadataReader(
tokens.get(1),api).completeType());for(int i=2;i<tokens.size();i++){field.metas.add(meta(tokens.get(i),api));
}type.fields.add(field);return;}String verb=null;String path=null;for(int i=0;i<signature;i++){String token=tokens.get(i);
if(token.startsWith("/")||token.startsWith("\"")){path=token.startsWith("\"")?new MetadataReader(token,api).string():token;
}else{verb=token;}}String signatureToken=tokens.get(signature);String name=signatureToken.substring(0,signatureToken.indexOf('('));
MethodDecl method=new MethodDecl(verb,path,"new".equals(name)?null:name);String parameters=signatureToken.substring(signatureToken.indexOf('(')+1,
signatureToken.lastIndexOf(')'));if(!parameters.trim().isEmpty()){for(String parameter:split(parameters,',')){
ParamDecl decl=new ParamDecl();for(String token:split(parameter.trim(),' ')){if(isMeta(token)){decl.metas.add(meta(token,api));
}else{decl.type=new MetadataReader(token,api).completeType();}}method.parameters.add(decl);}}int next=signature+1;
if(!method.isConstructor()){method.returnType=new MetadataReader(tokens.get(next++),api).completeType();
}for(int i=next;i<tokens.size();i++){method.metas.add(meta(tokens.get(i),api));}type.methods.add(method);
}private static boolean isMeta(String token){return token.startsWith("@")||token.startsWith("=")||token.matches("[pqmhcf]:.*");
}private static Meta meta(String token,boolean api){MetadataReader reader=new MetadataReader(token,api);
if(token.startsWith("@")){AnnotationRef annotation=reader.annotation();reader.expectEnd();return annotation;
}if(api){if(token.startsWith("/")){return new PathMeta(token);}if(token.startsWith("\"")){return new PathMeta(reader.string());
}if(token.startsWith(">")||token.startsWith("<")){MediaTypes mediaTypes=new MediaTypes(token.startsWith(">"));
for(String mediaType:split(token.substring(1),',')){mediaTypes.types.add(mediaType.startsWith("\"")?new MetadataReader(mediaType,api).string():
mediaType);}return mediaTypes;}if(token.startsWith("=")){return new Resolution(token.substring(1));}
if(token.matches("[pqmhcf]:.*")){reader.position=2;String name=reader.peek('"')?reader.string():reader.until('=');
String defaultValue=null;if(reader.accept('=')){defaultValue=reader.peek('"')?reader.string():reader.until((char)0);
}reader.expectEnd();return new ParamSource(token.charAt(0),name,defaultValue);}}throw new IllegalArgumentException("unknown token: "+token);
}private TypeRef completeType(){TypeRef result=type();expectEnd();return result;}private TypeRef type(){
skipSpaces();List<AnnotationRef>annotations=new ArrayList<AnnotationRef>();while(peek('@')){annotations.add(annotation());
skipSpaces();}TypeRef result;if(accept('?')){result=new TypeRef("?");skipSpaces();for(String kind:new String[]{
"extends","super"}){if(text.startsWith(kind+" ",position)){position+=kind.length()+1;result.boundKind=kind;
result.bound=type();}}}else{result=new TypeRef(qualifiedName());if(accept('<')){do{result.arguments.add(type());
skipSpaces();}while(accept(','));expect('>');}}result.annotations.addAll(annotations);while(text.startsWith("[]",position)){
position+=2;result.dimensions++;}return result;}private AnnotationRef annotation(){expect('@');AnnotationRef result=new AnnotationRef(qualifiedName())
;if(accept('(')){do{skipSpaces();int start=position;String memberName=identifierOrNull();skipSpaces();
if(memberName!=null&&peek('=')){position++;}else{position=start;memberName="value";}result.members.put(memberName,value());
skipSpaces();}while(accept(','));expect(')');}return result;}private Value value(){skipSpaces();if(peek('"')){
return new Value(Value.Kind.STRING,string(),null);}if(peek('\'')){position++;String content=unescape('\'');
return new Value(Value.Kind.CHAR,content,null);}if(peek('@')){return new Value(Value.Kind.ANNOTATION,null,annotation());
}if(accept('{')){Value result=new Value(Value.Kind.ARRAY,null,null);skipSpaces();if(!peek('}')){do{result.elements.add(value());
skipSpaces();}while(accept(','));}expect('}');return result;}int start=position;while(position<text.length()&&",)} ".indexOf(text.charAt(position))<0)
{position++;}if(start==position){throw error("value expected");}return new Value(Value.Kind.RAW,text.substring(start,position),null);
}private String string(){expect('"');return unescape('"');}private String unescape(char end){StringBuilder result=new StringBuilder();
while(true){if(position>=text.length()){throw error("unterminated literal");}char c=text.charAt(position++);
if(c==end){return result.toString();}if(c=='\\'){char escaped=text.charAt(position++);switch(escaped){
case'n':result.append('\n');break;case'r':result.append('\r');break;case't':result.append('\t');break;
case'u':result.append((char)Integer.parseInt(text.substring(position,position+4),16));position+=4;break;
default:result.append(escaped);}}else{result.append(c);}}}private String qualifiedName(){int start=position;
while(position<text.length()&&(Character.isJavaIdentifierPart(text.charAt(position))||text.charAt(position)=='.')){
position++;}if(start==position){throw error("name expected");}return text.substring(start,position);
}private String identifierOrNull(){int start=position;while(position<text.length()&&Character.isJavaIdentifierPart(text.charAt(position))){
position++;}return start==position?null:text.substring(start,position);}private String until(char end){
int start=position;while(position<text.length()&&text.charAt(position)!=end){position++;}return text.substring(start,position);
}private boolean peek(char c){return position<text.length()&&text.charAt(position)==c;}private boolean accept(char c){
if(peek(c)){position++;return true;}return false;}private void expect(char c){if(!accept(c)){throw error("'"+c+"' expected");
}}private void expectEnd(){skipSpaces();if(position<text.length()){throw error("unexpected '"+text.substring(position)+"'");
}}private void skipSpaces(){while(position<text.length()&&text.charAt(position)==' '){position++;}}private IllegalArgumentException error(String
message){return new IllegalArgumentException(message+" at "+position+" of: "+text);}static List<String>split(String text,char separator){
List<String>result=new ArrayList<String>();int depth=0;int angle=0;char quote=0;int start=0;for(int i=0;
i<text.length();i++){char c=text.charAt(i);if(quote!=0){if(c=='\\'){i++;}else if(c==quote){quote=0;}
}else if(c=='"'||c=='\''){quote=c;}else if(c=='('||c=='{'||c=='['){depth++;}else if(c==')'||c=='}'||c==']'){
depth--;}else if(c=='<'&&i>start&&(Character.isJavaIdentifierPart(text.charAt(i-1))||text.charAt(i-1)=='>')){
angle++;}else if(c=='>'&&angle>0){angle--;}else if(c==separator&&depth==0&&angle==0){if(i>start){result.add(text.substring(start,i));
}start=i+1;}}if(start<text.length()){result.add(text.substring(start));}return result;}}public final static class MetadataRenderer{
private final List<String>packages=new ArrayList<String>();private final Set<String>types=new LinkedHashSet<String>();
private final Set<String>metaAnnotations=new LinkedHashSet<String>();private final ShortNames names=new ShortNames();
public MetadataRenderer(Collection<String>packages,Collection<String>types,Collection<String>metaAnnotations){
this.packages.addAll(packages);this.types.addAll(types);this.metaAnnotations.addAll(metaAnnotations);
}public String name(Class<?>clazz){return names.nameOf(clazz,TypeNames.simpleName(clazz));}public String typeParameters(TypeVariable<?>[]parameters){
if(parameters.length==0){return"";}StringBuilder result=new StringBuilder("<");for(int i=0;i<parameters.length;
i++){result.append(i>0?",":"").append(parameters[i].getName());Type[]bounds=parameters[i].getBounds();
if(!(bounds.length==1&&bounds[0]==Object.class)){result.append(" extends ");for(int b=0;b<bounds.length;
b++){result.append(b>0?"&":"").append(type(bounds[b],null));}}}return result.append('>').toString();
}public String type(Type type,AnnotatedType annotatedType){if(type instanceof Class){Class<?>clazz=(Class<?>)type;
return clazz.isArray()?type(clazz.getComponentType(),null)+"[]":name(clazz);}if(type instanceof ParameterizedType){
ParameterizedType parameterized=(ParameterizedType)type;Type[]arguments=parameterized.getActualTypeArguments();
AnnotatedType[]annotatedArguments=annotatedType instanceof AnnotatedParameterizedType?((AnnotatedParameterizedType)annotatedType).
getAnnotatedActualTypeArguments():null;StringBuilder result=new StringBuilder(type(parameterized.getRawType(),null)).append('<');
for(int i=0;i<arguments.length;i++){result.append(i>0?",":"");AnnotatedType annotatedArgument=annotatedArguments==null?null:annotatedArguments[i];
if(annotatedArgument!=null){for(Annotation annotation:annotatedArgument.getAnnotations()){if(isIncluded(annotation)){
result.append(annotation(annotation)).append(' ');}}}result.append(type(arguments[i],annotatedArgument));
}return result.append('>').toString();}if(type instanceof GenericArrayType){return type(((GenericArrayType)type).getGenericComponentType(),null)+"[]";
}if(type instanceof WildcardType){WildcardType wildcard=(WildcardType)type;if(wildcard.getLowerBounds().length>0){
return"? super "+type(wildcard.getLowerBounds()[0],null);}Type upper=wildcard.getUpperBounds()[0];return upper==Object.class?"?":"? extends "+type(
upper,null);}return type.toString();}public String annotation(Annotation annotation){List<String>members=new ArrayList<String>();
String singleValue=null;for(Method member:members(annotation)){Object value=value(member,annotation);
if(Objects.deepEquals(value,member.getDefaultValue())){continue;}String rendered=annotationValue(value);
members.add(member.getName()+"="+rendered);if("value".equals(member.getName())){singleValue=rendered;
}}String result="@"+name(annotation.annotationType());if(members.isEmpty()){return result;}if(members.size()==1&&singleValue!=null){
return result+"("+singleValue+")";}StringBuilder joined=new StringBuilder(result).append('(');for(int i=0;
i<members.size();i++){joined.append(i>0?",":"").append(members.get(i));}return joined.append(')').toString();
}private String annotationValue(Object value){if(value.getClass().isArray()){int length=Array.getLength(value);
if(length==1){return annotationValue(Array.get(value,0));}StringBuilder result=new StringBuilder("{");
for(int i=0;i<length;i++){result.append(i>0?",":"").append(annotationValue(Array.get(value,i)));}return result.append('}').toString();
}if(value instanceof String){return quote((String)value);}if(value instanceof Character){String quoted=quote(String.valueOf(value));
return"'"+("'".equals(String.valueOf(value))?"\\'":quoted.substring(1,quoted.length()-1))+"'";}if(value instanceof Enum){
return((Enum<?>)value).name();}if(value instanceof Class){return type((Class<?>)value,null);}if(value instanceof Annotation){
return annotation((Annotation)value);}return String.valueOf(value);}public boolean isIncluded(Annotation annotation){
Class<?extends Annotation>annotationType=annotation.annotationType();if(types.contains(annotationType.getName())){
return true;}String packageName=packageName(annotationType);for(String included:packages){if(inPackage(packageName,included)){
return true;}}if(!metaAnnotations.isEmpty()){for(Annotation meta:annotationType.getDeclaredAnnotations()){
if(metaAnnotations.contains(meta.annotationType().getName())){return true;}}}return false;}public List<Annotation>included(AnnotatedElement element){
List<Annotation>result=new ArrayList<Annotation>();for(Annotation annotation:element.getDeclaredAnnotations()){
if(isIncluded(annotation)){result.add(annotation);}}return result;}public List<Annotation>included(AnnotatedElement element,AnnotatedType annotatedType
){List<Annotation>result=included(element);if(annotatedType!=null){for(Annotation annotation:annotatedType.getAnnotations()){
if(isIncluded(annotation)&&!result.contains(annotation)){result.add(annotation);}}}return result;}public static String quote(String value){
StringBuilder result=new StringBuilder("\"");for(char c:value.toCharArray()){switch(c){case'"':result.append("\\\"");
break;case'\\':result.append("\\\\");break;case'\n':result.append("\\n");break;case'\r':result.append("\\r");
break;case'\t':result.append("\\t");break;default:if(c<0x20){result.append(String.format("\\u%04x",(int)c));
}else{result.append(c);}}}return result.append('"').toString();}public static List<Method>members(Annotation annotation){
List<Method>result=new ArrayList<Method>(Arrays.asList(annotation.annotationType().getDeclaredMethods()));
Collections.sort(result,new Comparator<Method>(){public int compare(Method a,Method b){return a.getName().compareTo(b.getName());
}});return result;}public static Object value(Method member,Annotation annotation){try{member.setAccessible(true);
return member.invoke(annotation);}catch(Exception e){throw new IllegalStateException("can't read "+member+" of "+annotation,e);
}}public static Object value(Annotation annotation,String memberName){try{return value(annotation.annotationType().getDeclaredMethod(memberName),
annotation);}catch(NoSuchMethodException e){throw new IllegalStateException(e);}}public static Annotation find(AnnotatedElement element,String...
annotationTypeNames){return find(element.getDeclaredAnnotations(),annotationTypeNames);}public static Annotation find(Annotation[]annotations,String
...annotationTypeNames){for(Annotation annotation:annotations){if(is(annotation,annotationTypeNames)){
return annotation;}}return null;}public static boolean is(Annotation annotation,String...annotationTypeNames){
String name=annotation.annotationType().getName();for(String candidate:annotationTypeNames){if(name.equals(candidate)){
return true;}}return false;}public static String[]bothNamespaces(String suffix){return new String[]{
"jakarta."+suffix,"javax."+suffix};}public static String packageName(Class<?>clazz){String name=clazz.getName();
int lastDot=name.lastIndexOf('.');return lastDot<0?"":name.substring(0,lastDot);}public static boolean inPackage(String packageName,String prefix){
return packageName.equals(prefix)||packageName.startsWith(prefix+".");}public static boolean isPlatformType(Class<?>clazz){
String name=clazz.getName();return clazz.isPrimitive()||name.startsWith("java.")||name.startsWith("javax.")||name.startsWith("jakarta.")||name.
startsWith("jdk.")||name.startsWith("sun.");}public static Class<?>loadOrNull(String name,ClassLoader loader){
try{return Class.forName(name,false,loader);}catch(Throwable e){return null;}}}public abstract static class SourceGenerator<SELF extends
SourceGenerator<SELF>>{private static final List<String>JDK_PACKAGES=Arrays.asList("java.lang","java.util","java.util.concurrent","java.math",
"java.time","java.io","java.net","java.lang.annotation");private static final Set<String>PRIMITIVES=new HashSet<String>(Arrays.asList("boolean","byte"
,"char","short","int","long","float","double","void"));private static final Set<String>KEYWORDS=new HashSet<String>(Arrays.asList("do","if","for",
"int","new","try","case","char","else","enum","goto","long","this","void"));protected final List<TypeDecl>types;
protected final Map<String,TypeDecl>model=new LinkedHashMap<String,TypeDecl>();private final List<String>packages=new ArrayList<String>();
private final Map<String,String>mappings=new HashMap<String,String>();private final Map<String,String>resolved=new HashMap<String,String>();
private String basePackage="generated";private ClassLoader classLoader;private boolean javax;protected SourceGenerator(List<TypeDecl>types){
this.types=types;for(TypeDecl type:types){model.put(type.name,type);}}private SELF self(){return(SELF)this;
}public SELF basePackage(String packageName){basePackage=packageName;return self();}public SELF packages(String...packageNames){
packages.addAll(Arrays.asList(packageNames));return self();}public SELF map(String name,String fullyQualifiedName){
mappings.put(name,fullyQualifiedName);return self();}public SELF javax(){javax=true;return self();}public SELF classLoader(ClassLoader loader){
classLoader=loader;return self();}public Map<String,String>generate(){Map<String,String>result=new TreeMap<String,String>();
for(TypeDecl type:types){SourceFile file=new SourceFile(fullyQualifiedName(type));String body=body(type,file);
result.put(file.path(),file.render(body));}result.putAll(additionalSources());return result;}public void writeTo(Path directory)throws IOException{
for(Map.Entry<String,String>source:generate().entrySet()){Path file=directory.resolve(source.getKey());
Files.createDirectories(file.getParent());try(OutputStream out=Files.newOutputStream(file)){out.write(source.getValue().getBytes(StandardCharsets.
UTF_8));}}}protected abstract String body(TypeDecl type,SourceFile file);protected abstract List<String>frameworkPackages(String namespace);
protected Map<String,String>frameworkMappings(String namespace){return new HashMap<String,String>();
}protected Map<String,String>additionalSources(){return new HashMap<String,String>();}protected String namespace(){
return javax?"javax":"jakarta";}protected String basePackage(){return basePackage;}protected String fullyQualifiedName(TypeDecl type){
String syntheticPackage=type.syntheticPackage();String packageName=basePackage;if(!syntheticPackage.isEmpty()){
for(String part:syntheticPackage.split("\\.")){packageName+="."+(KEYWORDS.contains(part)?part+"_":part);
}}return packageName+"."+type.simpleName();}protected String resolve(String name){String result=resolved.get(name);
if(result==null){result=doResolve(name);resolved.put(name,result);}return result;}private String doResolve(String name){
TypeDecl type=model.get(name);if(type!=null){return fullyQualifiedName(type);}if(mappings.containsKey(name)){
return mappings.get(name);}Map<String,String>defaults=frameworkMappings(namespace());if(defaults.containsKey(name)){
return defaults.get(name);}int lastDot=name.lastIndexOf('.');String simpleName=name.substring(lastDot+1);
int wanted=lastDot<0?0:ordinal(name.substring(0,lastDot));Set<Integer>takenByModel=new HashSet<Integer>();
for(TypeDecl candidate:types){if(candidate.simpleName().equals(simpleName)){takenByModel.add(candidate.syntheticPackage().isEmpty()?0:ordinal(
candidate.syntheticPackage()));}}List<String>library=new ArrayList<String>();List<String>searched=new ArrayList<String>(frameworkPackages(namespace())
);searched.addAll(JDK_PACKAGES);searched.addAll(packages);for(String packageName:searched){String candidate=packageName+"."+simpleName;
if(!library.contains(candidate)&&load(candidate)!=null){library.add(candidate);}}int ordinal=0;for(String candidate:library){
while(takenByModel.contains(ordinal)){ordinal++;}if(ordinal==wanted){return candidate;}ordinal++;}throw new IllegalArgumentException("can't resolve "+
name+" - add its package or a mapping");}private static int ordinal(String syntheticPackage){int result=0;
for(char c:syntheticPackage.toCharArray()){if(c<'a'||c>'z'){return-1;}result=result*26+(c-'a'+1);}return result;
}protected Class<?>load(String className){ClassLoader loader=classLoader!=null?classLoader:Thread.currentThread().getContextClassLoader();
if(loader==null){loader=SourceGenerator.class.getClassLoader();}String name=className;while(true){try{
return Class.forName(name,false,loader);}catch(ClassNotFoundException|LinkageError e){int lastDot=name.lastIndexOf('.');
if(lastDot<0){return null;}name=name.substring(0,lastDot)+'$'+name.substring(lastDot+1);if(name.indexOf('.')<0){
return null;}}}}protected boolean isInterface(TypeRef type){TypeDecl decl=model.get(type.name);if(decl!=null){
return decl.kind==MetadataModel.Kind.INTERFACE;}Class<?>clazz=load(resolve(type.name));return clazz!=null&&clazz.isInterface();
}protected String typeParameters(List<TypeParam>parameters,SourceFile file,Set<String>variables){if(parameters.isEmpty()){
return"";}StringBuilder result=new StringBuilder("<");for(int i=0;i<parameters.size();i++){TypeParam parameter=parameters.get(i);
result.append(i>0?", ":"").append(parameter.name);for(int b=0;b<parameter.bounds.size();b++){result.append(b==0?" extends ":" & ").append(type(
parameter.bounds.get(b),file,variables));}}return result.append('>').toString();}protected static Set<String>variables(List<TypeParam>parameters){
Set<String>result=new HashSet<String>();for(TypeParam parameter:parameters){result.add(parameter.name);
}return result;}protected String type(TypeRef type,SourceFile file,Set<String>variables){StringBuilder annotations=new StringBuilder();
for(AnnotationRef annotation:type.annotations){annotations.append(annotation(annotation,file,null)).append(' ');
}StringBuilder result=new StringBuilder();if(type.isWildcard()){result.append(annotations).append('?');
if(type.bound!=null){result.append(' ').append(type.boundKind).append(' ').append(type(type.bound,file,variables));
}return result.toString();}String name=PRIMITIVES.contains(type.name)||variables.contains(type.name)?type.name:file.ref(resolve(type.name));
int lastDot=name.lastIndexOf('.');if(annotations.length()>0&&lastDot>0){result.append(name,0,lastDot+1).append(annotations).append(name.substring(
lastDot+1));}else{result.append(annotations).append(name);}if(!type.arguments.isEmpty()){result.append('<');
for(int i=0;i<type.arguments.size();i++){result.append(i>0?", ":"").append(type(type.arguments.get(i),file,variables));
}result.append('>');}for(int i=0;i<type.dimensions;i++){result.append("[]");}return result.toString();
}protected String annotations(Collection<Meta>metas,SourceFile file,String separator){StringBuilder result=new StringBuilder();
for(Meta meta:metas){if(meta instanceof AnnotationRef){result.append(annotation((AnnotationRef)meta,file,null)).append(separator);
}}return result.toString();}protected String annotation(AnnotationRef annotation,SourceFile file,Class<?>annotationType){
String annotationName=annotationType!=null?annotationType.getCanonicalName():resolve(annotation.name);
Class<?>resolvedType=annotationType!=null?annotationType:load(annotationName);StringBuilder result=new StringBuilder("@").append(file.ref(
annotationName));if(annotation.members.isEmpty()){return result.toString();}result.append('(');boolean first=true;
for(Map.Entry<String,Value>member:annotation.members.entrySet()){Class<?>memberType=memberType(resolvedType,member.getKey());
result.append(first?"":", ");if(!(annotation.members.size()==1&&"value".equals(member.getKey()))){result.append(member.getKey()).append(" = ");
}result.append(value(member.getValue(),memberType,file));first=false;}return result.append(')').toString();
}private static Class<?>memberType(Class<?>annotationType,String member){if(annotationType==null){return null;
}try{return annotationType.getMethod(member).getReturnType();}catch(NoSuchMethodException e){throw new IllegalArgumentException(annotationType.getName
()+" has no member "+member);}}private String value(Value value,Class<?>type,SourceFile file){Class<?>elementType=type!=null&&type.isArray()?type.
getComponentType():type;switch(value.kind){case ARRAY:StringBuilder result=new StringBuilder("{");for(int i=0;
i<value.elements.size();i++){result.append(i>0?", ":"").append(value(value.elements.get(i),elementType,file));
}return result.append('}').toString();case STRING:return MetadataRenderer.quote(value.text);case CHAR:return"'"+("'".equals(value.text)?"\\'":
MetadataRenderer.quote(value.text).replaceAll("^\"|\"$",""))+"'";case ANNOTATION:return annotation(value.annotation,file,elementType);
default:return raw(value.text,elementType,file);}}private String raw(String text,Class<?>type,SourceFile file){
if(type==null){return text;}if(type.isEnum()){return file.ref(type.getCanonicalName())+"."+text;}if(type==Class.class){
return type(MetadataReader.parseType(text),file,new HashSet<String>())+".class";}if(type==long.class){
return text.endsWith("L")?text:text+"L";}if(type==float.class||type==double.class){String wrapper=type==float.class?"Float":"Double";
if("NaN".equals(text)){return wrapper+".NaN";}if(text.endsWith("Infinity")){return wrapper+(text.startsWith("-")?".NEGATIVE_INFINITY":
".POSITIVE_INFINITY");}return type==float.class?text+"f":text;}if(type==byte.class||type==short.class){
return"("+type.getName()+") "+text;}return text;}protected static String capitalize(String name){return Character.toUpperCase(name.charAt(0))+name.
substring(1);}public final class SourceFile{private final String packageName;private final String fullyQualifiedName;
private final Map<String,String>imports=new TreeMap<String,String>();private final Map<String,String>bound=new HashMap<String,String>();
SourceFile(String fullyQualifiedName){this.fullyQualifiedName=fullyQualifiedName;this.packageName=fullyQualifiedName.substring(0,fullyQualifiedName.
lastIndexOf('.'));for(TypeDecl type:types){String name=fullyQualifiedName(type);if(name.substring(0,name.lastIndexOf('.')).equals(packageName)){
bound.put(type.simpleName(),name);}}}public String ref(String canonicalName){int lastDot=canonicalName.lastIndexOf('.');
if(lastDot<0){return canonicalName;}String simpleName=canonicalName.substring(lastDot+1);String owner=canonicalName.substring(0,lastDot);
String existing=bound.get(simpleName);if(existing!=null){return existing.equals(canonicalName)?simpleName:canonicalName;
}bound.put(simpleName,canonicalName);if(!owner.equals(packageName)&&!owner.equals("java.lang")){imports.put(canonicalName,canonicalName);
}return simpleName;}String path(){return fullyQualifiedName.replace('.','/')+".java";}String render(String body){
StringBuilder result=new StringBuilder("package ").append(packageName).append(";\n\n");for(String anImport:imports.keySet()){
result.append("import ").append(anImport).append(";\n");}if(!imports.isEmpty()){result.append('\n');
}return result.append(body).toString();}}}}

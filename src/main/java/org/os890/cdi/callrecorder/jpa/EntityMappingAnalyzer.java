// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.jpa;import java.io.IOException;import java.lang.annotation.Annotation;
import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
import org.os890.cdi.callrecorder.analysis.MetadataModel.*;import static org.os890.cdi.callrecorder.analysis.MetadataModel.MetadataRenderer.*;
public final class EntityMappingAnalyzer{private static final String[]PERSISTENCE_PACKAGES={"jakarta.persistence","javax.persistence"}
;private static final String[]MAPPED_TYPE_ANNOTATIONS={"Entity","MappedSuperclass","Embeddable"};private final Class<?>type;
private final List<String>annotationPackages=new ArrayList<String>();private final Set<String>annotationTypes=new LinkedHashSet<String>();
private final List<String>scanPackages=new ArrayList<String>();private final Set<Class<?>>classes=new LinkedHashSet<Class<?>>();
private final Set<Class<?>>plainWithAttributes=new LinkedHashSet<Class<?>>();private final Set<Class<?>>enums=new LinkedHashSet<Class<?>>();
private final Deque<Class<?>>pending=new ArrayDeque<Class<?>>();private MetadataRenderer renderer;private ClassIndex index;
private EntityMappingAnalyzer(Class<?>type){this.type=type;}public static String analyze(Class<?>type,String...additionalAnnotationPackages){
return of(type).includeAnnotationPackages(additionalAnnotationPackages).analyze();}public static EntityMappingAnalyzer of(Class<?>type){
return new EntityMappingAnalyzer(type);}public EntityMappingAnalyzer includeAnnotationPackages(String...packages){
annotationPackages.addAll(Arrays.asList(packages));return this;}@SafeVarargs public final EntityMappingAnalyzer includeAnnotations(Class<?extends
Annotation>...annotations){for(Class<?extends Annotation>annotation:annotations){annotationTypes.add(annotation.getName());
}return this;}public EntityMappingAnalyzer scanPackages(String...packages){scanPackages.addAll(Arrays.asList(packages));
return this;}public static void main(String[]args)throws ClassNotFoundException{if(args.length==0){System.err.println("usage: "+EntityMappingAnalyzer.
class.getName()+" <type> [<annotation package or annotation type>...]");return;}ClassLoader loader=Thread.currentThread().getContextClassLoader();
EntityMappingAnalyzer analyzer=of(Class.forName(args[0],false,loader));for(int i=1;i<args.length;i++){
Class<?>annotation=loadOrNull(args[i],loader);if(annotation!=null&&annotation.isAnnotation()){analyzer.annotationTypes.add(annotation.getName());
}else{analyzer.annotationPackages.add(args[i]);}}System.out.print(analyzer.analyze());}public synchronized String analyze(){
classes.clear();plainWithAttributes.clear();enums.clear();index=ClassIndex.scan(type,scanPackages);List<String>includedPackages=new ArrayList<String>(
Arrays.asList(PERSISTENCE_PACKAGES));includedPackages.addAll(annotationPackages);renderer=new MetadataRenderer(includedPackages,annotationTypes,
Collections.<String>emptyList());addWithHierarchy(type);while(!pending.isEmpty()){discoverReferences(pending.poll());
}StringBuilder result=new StringBuilder();for(Class<?>root:roots()){write(root,result);}for(Class<?>anEnum:enums){
result.append(name(anEnum)).append('{');boolean first=true;for(Field field:anEnum.getDeclaredFields()){
if(field.isEnumConstant()){result.append(first?"":",").append(field.getName());first=false;}}result.append("}\n");
}return result.toString();}private void addWithHierarchy(Class<?>start){addAncestors(start);for(String subtypeName:index.subtypesOf(start.getName())){
Class<?>subtype=loadOrNull(subtypeName,start.getClassLoader());if(subtype!=null&&isMapped(subtype)){
addAncestors(subtype);}}}private void addAncestors(Class<?>start){List<Class<?>>chain=new ArrayList<Class<?>>();
int topmostMapped=-1;for(Class<?>current=start;current!=null&&current!=Object.class;current=current.getSuperclass()){
chain.add(current);if(isMapped(current)){topmostMapped=chain.size()-1;}}for(int i=topmostMapped;i>=0;
i--){add(chain.get(i));}}private void add(Class<?>mappedOrPlain){if(classes.add(mappedOrPlain)){pending.add(mappedOrPlain);
}}private void discoverReferences(Class<?>type){for(Annotation annotation:renderer.included(type)){discoverInAnnotation(annotation);
}for(Attribute attribute:attributes(type)){discoverInType(attribute.genericType);for(Annotation annotation:attribute.annotations){
discoverInAnnotation(annotation);}}}private void discoverInType(Type type){if(type instanceof Class){
Class<?>clazz=(Class<?>)type;while(clazz.isArray()){clazz=clazz.getComponentType();}if(clazz.isEnum()){
enums.add(clazz);}else if(isMapped(clazz)&&!classes.contains(clazz)){addWithHierarchy(clazz);}}else if(type instanceof ParameterizedType){
for(Type argument:((ParameterizedType)type).getActualTypeArguments()){discoverInType(argument);}}else if(type instanceof GenericArrayType){
discoverInType(((GenericArrayType)type).getGenericComponentType());}else if(type instanceof WildcardType){
for(Type bound:((WildcardType)type).getUpperBounds()){discoverInType(bound);}}}private void discoverInAnnotation(Annotation annotation){
boolean idClass=is(annotation,"IdClass");for(Method member:members(annotation)){Object value=value(member,annotation);
Object[]values=value!=null&&value.getClass().isArray()&&!value.getClass().getComponentType().isPrimitive()?(Object[])value:new Object[]{
value};for(Object element:values){if(element instanceof Class){Class<?>clazz=(Class<?>)element;if(isMapped(clazz)){
discoverInType(clazz);}else if(idClass&&plainWithAttributes.add(clazz)){add(clazz);}}else if(element instanceof Annotation){
discoverInAnnotation((Annotation)element);}}}}private List<Class<?>>roots(){List<Class<?>>result=new ArrayList<Class<?>>();
for(Class<?>clazz:classes){if(!classes.contains(clazz.getSuperclass())){result.add(clazz);}}return result;
}private void write(Class<?>clazz,StringBuilder result){if(Modifier.isAbstract(clazz.getModifiers())){
result.append('~');}result.append(name(clazz)).append(renderer.typeParameters(clazz.getTypeParameters()));
if(classes.contains(clazz.getSuperclass())){result.append(':').append(renderer.type(clazz.getGenericSuperclass(),null));
}appendAnnotations(renderer.included(clazz),result);result.append('\n');for(Attribute attribute:attributes(clazz)){
result.append(' ').append(attribute.name).append(attribute.property?"()":"").append(' ').append(renderer.type(attribute.genericType,attribute.
annotatedType));appendAnnotations(attribute.annotations,result);result.append('\n');}List<Class<?>>children=new ArrayList<Class<?>>();
for(Class<?>candidate:classes){if(candidate.getSuperclass()==clazz){children.add(candidate);}}Collections.sort(children,new Comparator<Class<?>>(){
public int compare(Class<?>a,Class<?>b){int result=a.getSimpleName().compareTo(b.getSimpleName());return result!=0?result:a.getName().compareTo(b.
getName());}});for(Class<?>child:children){write(child,result);}}private void appendAnnotations(List<Annotation>annotations,StringBuilder result){
for(Annotation annotation:annotations){result.append(' ').append(renderer.annotation(annotation));}}
private List<Attribute>attributes(Class<?>clazz){List<Attribute>result=new ArrayList<Attribute>();boolean mapped=isMapped(clazz);
if(!mapped&&!plainWithAttributes.contains(clazz)){return result;}boolean propertyAccess=mapped&&"PROPERTY".equals(accessType(clazz));
for(Field field:clazz.getDeclaredFields()){int modifiers=field.getModifiers();if(Modifier.isStatic(modifiers)||Modifier.isTransient(modifiers)||field.
isSynthetic()||hasTransient(field)){continue;}String explicitAccess=explicitAccess(field);if(explicitAccess!=null?"FIELD".equals(explicitAccess):!
propertyAccess){result.add(new Attribute(field.getName(),false,field.getGenericType(),field.getAnnotatedType(),renderer.included(field,field.
getAnnotatedType())));}}if(mapped){List<Method>getters=new ArrayList<Method>();for(Method method:clazz.getDeclaredMethods()){
if(propertyName(method)!=null&&!hasTransient(method)){String explicitAccess=explicitAccess(method);if(explicitAccess!=null?"PROPERTY".equals(
explicitAccess):propertyAccess){getters.add(method);}}}Collections.sort(getters,new Comparator<Method>(){
public int compare(Method a,Method b){return propertyName(a).compareTo(propertyName(b));}});for(Method getter:getters){
result.add(new Attribute(propertyName(getter),true,getter.getGenericReturnType(),getter.getAnnotatedReturnType(),renderer.included(getter,getter.
getAnnotatedReturnType())));}}return result;}private String accessType(Class<?>clazz){Annotation access=persistenceAnnotation(clazz,"Access");
if(access!=null){return((Enum<?>)value(access,"value")).name();}List<Class<?>>candidates=new ArrayList<Class<?>>();
for(Class<?>current=clazz;current!=null&&current!=Object.class;current=current.getSuperclass()){candidates.add(current);
}for(Class<?>other:classes){if(other!=clazz&&clazz.isAssignableFrom(other)){candidates.add(other);}}
for(Class<?>candidate:candidates){for(Field field:candidate.getDeclaredFields()){if(persistenceAnnotation(field,"Id")!=null||persistenceAnnotation(
field,"EmbeddedId")!=null){return"FIELD";}}for(Method method:candidate.getDeclaredMethods()){if(persistenceAnnotation(method,"Id")!=null||
persistenceAnnotation(method,"EmbeddedId")!=null){return"PROPERTY";}}}for(Field field:clazz.getDeclaredFields()){
if(hasPersistenceAnnotation(field)){return"FIELD";}}for(Method method:clazz.getDeclaredMethods()){if(propertyName(method)!=null&&
hasPersistenceAnnotation(method)){return"PROPERTY";}}return"FIELD";}private String explicitAccess(AnnotatedElement element){
Annotation access=persistenceAnnotation(element,"Access");return access==null?null:((Enum<?>)value(access,"value")).name();
}private static String propertyName(Method method){int modifiers=method.getModifiers();if(Modifier.isStatic(modifiers)||method.isSynthetic()||method.
isBridge()||method.getParameterTypes().length>0){return null;}String name=method.getName();Class<?>returnType=method.getReturnType();
String property;if(name.startsWith("get")&&name.length()>3&&returnType!=void.class){property=name.substring(3);
}else if(name.startsWith("is")&&name.length()>2&&(returnType==boolean.class||returnType==Boolean.class)){
property=name.substring(2);}else{return null;}if(property.length()>1&&Character.isUpperCase(property.charAt(1))&&Character.isUpperCase(property.charAt
(0))){return property;}return Character.toLowerCase(property.charAt(0))+property.substring(1);}private boolean hasTransient(AnnotatedElement element){
return persistenceAnnotation(element,"Transient")!=null;}private static boolean isMapped(Class<?>clazz){
for(String annotation:MAPPED_TYPE_ANNOTATIONS){if(persistenceAnnotation(clazz,annotation)!=null){return true;
}}return false;}private static boolean hasPersistenceAnnotation(AnnotatedElement element){for(Annotation annotation:element.getDeclaredAnnotations()){
for(String persistencePackage:PERSISTENCE_PACKAGES){if(inPackage(packageName(annotation.annotationType()),persistencePackage)){
return true;}}}return false;}private String name(Class<?>clazz){return renderer.name(clazz);}private static Annotation persistenceAnnotation(
AnnotatedElement element,String simpleName){for(Annotation annotation:element.getDeclaredAnnotations()){
if(is(annotation,simpleName)){return annotation;}}return null;}private static boolean is(Annotation annotation,String persistenceSimpleName){
String name=annotation.annotationType().getName();for(String persistencePackage:PERSISTENCE_PACKAGES){
if(name.equals(persistencePackage+"."+persistenceSimpleName)){return true;}}return false;}private static final class Attribute{
final String name;final boolean property;final Type genericType;final AnnotatedType annotatedType;final List<Annotation>annotations;
Attribute(String name,boolean property,Type genericType,AnnotatedType annotatedType,List<Annotation>annotations){
this.name=name;this.property=property;this.genericType=genericType;this.annotatedType=annotatedType;
this.annotations=annotations;}}public final static class EntityMappingGenerator extends SourceGenerator<EntityMappingGenerator>{
private EntityMappingGenerator(List<TypeDecl>types){super(types);}public static EntityMappingGenerator of(String export){
return new EntityMappingGenerator(MetadataReader.readEntityMappings(export));}public static void main(String[]args)throws IOException{
if(args.length<2){System.err.println("usage: "+EntityMappingGenerator.class.getName()+
" <export file> <output directory> [--javax] [--package=<base package>] [<package>...]");return;}EntityMappingGenerator generator=of(new String(Files.
readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));for(int i=2;i<args.length;i++){if("--javax".equals(args[i])){
generator.javax();}else if(args[i].startsWith("--package=")){generator.basePackage(args[i].substring("--package=".length()));
}else{generator.packages(args[i]);}}generator.writeTo(Paths.get(args[1]));}protected List<String>frameworkPackages(String namespace){
return Collections.singletonList(namespace+".persistence");}protected String body(TypeDecl type,SourceFile file){
StringBuilder result=new StringBuilder();if(type.kind==Kind.ENUM){result.append("public enum ").append(type.simpleName()).append(" {\n");
result.append(type.constants.isEmpty()?"":"    "+String.join(", ",type.constants)+"\n");return result.append("}\n").toString();
}Set<String>variables=variables(type.typeParameters);result.append(annotations(type.metas,file,"\n"));
result.append("public ").append(type.kind==Kind.ABSTRACT_CLASS?"abstract ":"").append("class ").append(type.simpleName()).append(typeParameters(type.
typeParameters,file,variables));if(!type.supertypes.isEmpty()){result.append(" extends ").append(type(type.supertypes.get(0),file,variables));
}result.append(" {\n");for(FieldDecl field:type.fields){result.append('\n');if(!field.property){result.append(annotations(field.metas,file,"\n").
replaceAll("(?m)^","    "));}result.append("    private ").append(type(field.type,file,variables)).append(' ').append(field.name).append(";\n");
}for(FieldDecl field:type.fields){String fieldType=type(field.type,file,variables);result.append('\n');
if(field.property){result.append(annotations(field.metas,file,"\n").replaceAll("(?m)^","    "));}result.append("    public ").append(fieldType).append
(" get").append(capitalize(field.name)).append("() {\n").append("        return ").append(field.name).append(";\n    }\n\n").append(
"    public void set").append(capitalize(field.name)).append('(').append(fieldType).append(' ').append(field.name).append(") {\n        this.").append
(field.name).append(" = ").append(field.name).append(";\n    }\n");}return result.append("}\n").toString();
}}}

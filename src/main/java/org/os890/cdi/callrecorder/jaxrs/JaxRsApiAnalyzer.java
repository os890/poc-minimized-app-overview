// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.jaxrs;import java.io.*;import java.lang.annotation.Annotation;import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import org.os890.cdi.callrecorder.analysis.MetadataModel.*;
import org.os890.cdi.callrecorder.core.CallRecorder.TypeNames;import static org.os890.cdi.callrecorder.analysis.MetadataModel.MetadataRenderer.*;
public final class JaxRsApiAnalyzer{private static final String[]RS={"jakarta.ws.rs","javax.ws.rs"};
private static final String[]PATH=bothNamespaces("ws.rs.Path");private static final String[]HTTP_METHOD=bothNamespaces("ws.rs.HttpMethod");
private static final String[]RS_PRODUCES=bothNamespaces("ws.rs.Produces");private static final String[]RS_CONSUMES=bothNamespaces("ws.rs.Consumes");
private static final String[]DEFAULT_VALUE=bothNamespaces("ws.rs.DefaultValue");private static final String[]APPLICATION_PATH=bothNamespaces(
"ws.rs.ApplicationPath");private static final String[]APPLICATION=bothNamespaces("ws.rs.core.Application");
private static final Map<String,Character>PARAM_SOURCES=new LinkedHashMap<String,Character>();private static final String[]INJECT=bothNamespaces(
"inject.Inject");private static final String[]NAMED=bothNamespaces("inject.Named");private static final String[]QUALIFIER=bothNamespaces(
"inject.Qualifier");private static final String[]PROVIDER=bothNamespaces("inject.Provider");private static final String[]SCOPE=bothNamespaces(
"inject.Scope");private static final String[]NORMAL_SCOPE=bothNamespaces("enterprise.context.NormalScope");
private static final String[]STEREOTYPE=bothNamespaces("enterprise.inject.Stereotype");private static final String[]CDI_PRODUCES=bothNamespaces(
"enterprise.inject.Produces");private static final String[]DISPOSES=bothNamespaces("enterprise.inject.Disposes");
private static final String[]ALTERNATIVE=bothNamespaces("enterprise.inject.Alternative");private static final String[]DEFAULT=bothNamespaces(
"enterprise.inject.Default");private static final String[]ANY=bothNamespaces("enterprise.inject.Any");
private static final String[]TYPED=bothNamespaces("enterprise.inject.Typed");private static final String[]VETOED=bothNamespaces(
"enterprise.inject.Vetoed");private static final String[]INSTANCE=bothNamespaces("enterprise.inject.Instance");
private static final String[]EVENT=bothNamespaces("enterprise.event.Event");private static final String[]OBSERVES=bothNamespaces(
"enterprise.event.Observes");private static final String[]OBSERVES_ASYNC=bothNamespaces("enterprise.event.ObservesAsync");
private static final String[]NONBINDING=bothNamespaces("enterprise.util.Nonbinding");private static final String[]INTERCEPTOR=bothNamespaces(
"interceptor.Interceptor");private static final String[]INTERCEPTOR_BINDING=bothNamespaces("interceptor.InterceptorBinding");
private static final String[]DECORATOR=bothNamespaces("decorator.Decorator");private static final String[]DELEGATE=bothNamespaces("decorator.Delegate"
);private static final String[]PRIORITY=bothNamespaces("annotation.Priority");private static final List<String>DEFAULT_PACKAGES=new ArrayList<String>(
);private static final List<String>DEFAULT_TYPES=new ArrayList<String>();private static final List<String>META_ANNOTATIONS=new ArrayList<String>();
private static final List<String>MARKERS=new ArrayList<String>();private static final List<String>VERB_ORDER=Arrays.asList("GET","POST","PUT","PATCH",
"DELETE","HEAD","OPTIONS");static{PARAM_SOURCES.put("PathParam",'p');PARAM_SOURCES.put("QueryParam",'q');
PARAM_SOURCES.put("MatrixParam",'m');PARAM_SOURCES.put("HeaderParam",'h');PARAM_SOURCES.put("CookieParam",'c');
PARAM_SOURCES.put("FormParam",'f');for(String suffix:new String[]{"ws.rs","inject","enterprise.context","enterprise.inject","enterprise.event",
"interceptor","decorator"}){DEFAULT_PACKAGES.addAll(Arrays.asList(bothNamespaces(suffix)));}for(String suffix:new String[]{
"annotation.Priority","annotation.PostConstruct","annotation.PreDestroy"}){DEFAULT_TYPES.addAll(Arrays.asList(bothNamespaces(suffix)));
}for(String[]names:Arrays.asList(QUALIFIER,SCOPE,NORMAL_SCOPE,STEREOTYPE,INTERCEPTOR_BINDING,HTTP_METHOD)){
META_ANNOTATIONS.addAll(Arrays.asList(names));}for(String[]names:Arrays.asList(PATH,INTERCEPTOR,DECORATOR,CDI_PRODUCES,OBSERVES,OBSERVES_ASYNC)){
MARKERS.addAll(Arrays.asList(names));}}private final Class<?>type;private final List<String>annotationPackages=new ArrayList<String>();
private final Set<String>annotationTypes=new LinkedHashSet<String>();private final List<String>scanPackages=new ArrayList<String>();
private boolean keepStereotypes;private final Set<Class<?>>applications=new LinkedHashSet<Class<?>>();
private final Set<Class<?>>resources=new LinkedHashSet<Class<?>>();private final Set<Class<?>>beans=new LinkedHashSet<Class<?>>();
private final Set<Class<?>>stereotypes=new LinkedHashSet<Class<?>>();private final Set<Class<?>>dataTypes=new LinkedHashSet<Class<?>>();
private final Set<Class<?>>enums=new LinkedHashSet<Class<?>>();private final Map<AnnotatedElement,List<Target>>resolutions=new HashMap<
AnnotatedElement,List<Target>>();private final Map<Class<?>,Boolean>resourceRelated=new HashMap<Class<?>,Boolean>();
private final List<Class<?>>interceptors=new ArrayList<Class<?>>();private final List<Class<?>>decorators=new ArrayList<Class<?>>();
private final List<Member>producers=new ArrayList<Member>();private final List<Method>observers=new ArrayList<Method>();
private ClassIndex index;private MetadataRenderer renderer;private JaxRsApiAnalyzer(Class<?>type){this.type=type;
}public static String analyze(Class<?>type,String...additionalAnnotationPackages){return of(type).includeAnnotationPackages(
additionalAnnotationPackages).analyze();}public static JaxRsApiAnalyzer of(Class<?>type){return new JaxRsApiAnalyzer(type);
}public JaxRsApiAnalyzer includeAnnotationPackages(String...packages){annotationPackages.addAll(Arrays.asList(packages));
return this;}@SafeVarargs public final JaxRsApiAnalyzer includeAnnotations(Class<?extends Annotation>...annotations){
for(Class<?extends Annotation>annotation:annotations){annotationTypes.add(annotation.getName());}return this;
}public JaxRsApiAnalyzer scanPackages(String...packages){scanPackages.addAll(Arrays.asList(packages));
return this;}public JaxRsApiAnalyzer keepStereotypes(){keepStereotypes=true;return this;}public static void main(String[]args)throws
ClassNotFoundException{if(args.length==0){System.err.println("usage: "+JaxRsApiAnalyzer.class.getName()+
" <type> [--keep-stereotypes] [<annotation package or annotation type>...]");return;}ClassLoader loader=Thread.currentThread().getContextClassLoader()
;JaxRsApiAnalyzer analyzer=of(Class.forName(args[0],false,loader));for(int i=1;i<args.length;i++){if("--keep-stereotypes".equals(args[i])){
analyzer.keepStereotypes=true;continue;}Class<?>annotation=loadOrNull(args[i],loader);if(annotation!=null&&annotation.isAnnotation()){
analyzer.annotationTypes.add(annotation.getName());}else{analyzer.annotationPackages.add(args[i]);}}
System.out.print(analyzer.analyze());}public synchronized String analyze(){for(Set<Class<?>>set:Arrays.<Set<Class<?>>>asList(applications,resources,
beans,stereotypes,dataTypes,enums)){set.clear();}resolutions.clear();resourceRelated.clear();interceptors.clear();
decorators.clear();producers.clear();observers.clear();index=ClassIndex.scan(type,scanPackages,MARKERS);
List<String>includedPackages=new ArrayList<String>(DEFAULT_PACKAGES);includedPackages.addAll(annotationPackages);
List<String>includedTypes=new ArrayList<String>(DEFAULT_TYPES);includedTypes.addAll(annotationTypes);
renderer=new MetadataRenderer(includedPackages,includedTypes,META_ANNOTATIONS);indexCdiComponents();
if(isApplication(type)){applications.add(type);File location=ClassIndex.locationOf(type);for(String name:index.markedClasses()){
Class<?>candidate=loadOrNull(name,type.getClassLoader());if(candidate!=null&&find(candidate,PATH)!=null&&(!scanPackages.isEmpty()||location==null||
location.equals(index.locationOf(name)))){visitWithSubtypes(candidate);}}}else{visitWithSubtypes(type);
}StringBuilder result=new StringBuilder();for(Class<?>application:applications){result.append(name(application)).append(':').append(name(
superclassNamed(application,APPLICATION)));appendTokens(annotationTokens(cdiAnnotations(renderer.included(application)),application),result);
result.append('\n');}for(Class<?>resource:resources){writeType(resource,false,result);}for(Class<?>bean:beans){
writeType(bean,false,result);}for(Class<?>stereotype:stereotypes){result.append('@').append(name(stereotype));
if(stereotype.isAnnotationPresent(java.lang.annotation.Inherited.class)){result.append(" @Inherited");
}appendTokens(annotationTokens(renderer.included(stereotype),stereotype),result);result.append('\n');
}for(Class<?>dataType:dataTypes){writeType(dataType,true,result);}for(Class<?>anEnum:enums){result.append(name(anEnum)).append('{');
boolean first=true;for(Field field:anEnum.getDeclaredFields()){if(field.isEnumConstant()){result.append(first?"":",").append(field.getName());
first=false;}}result.append("}\n");}return result.toString();}private void indexCdiComponents(){ClassLoader loader=type.getClassLoader();
for(String name:index.markedClasses()){Class<?>candidate=loadOrNull(name,loader);if(candidate==null){
continue;}try{if(find(candidate,INTERCEPTOR)!=null){interceptors.add(candidate);}if(find(candidate,DECORATOR)!=null){
decorators.add(candidate);}for(Field field:candidate.getDeclaredFields()){if(find(field,CDI_PRODUCES)!=null){
producers.add(field);}}for(Method method:sortedMethods(candidate)){if(find(method,CDI_PRODUCES)!=null){
producers.add(method);}if(eventParameter(method)!=null){observers.add(method);}}}catch(LinkageError e){
}}}private void visitWithSubtypes(Class<?>start){visit(start);for(String subtypeName:index.subtypesOf(start.getName())){
Class<?>subtype=loadOrNull(subtypeName,start.getClassLoader());if(subtype!=null&&!subtype.isAnonymousClass()&&!subtype.isSynthetic()){
visit(subtype);}}}private void visit(Class<?>clazz){if(clazz==null||isPlatformType(clazz)||known(clazz)||clazz.isEnum()||clazz.isArray()){
return;}for(Class<?>supertype:supertypes(clazz)){visit(supertype);}if(known(clazz)){return;}(isResourceRelated(clazz)?resources:beans).add(clazz);
visitMembers(clazz);}private void visitMembers(Class<?>clazz){visitDataInSignature(clazz);if(keepStereotypes){
collectStereotypes(clazz.getDeclaredAnnotations());}for(Field field:listedFields(clazz,false)){if(keepStereotypes){
collectStereotypes(field.getDeclaredAnnotations());}if(find(field,INJECT)!=null){resolveInjectionPoint(field,field.getGenericType(),field.getName());
}else if(find(field,CDI_PRODUCES)==null){visitData(field.getGenericType());}}for(Constructor<?>constructor:listedConstructors(clazz)){
visitParameters(constructor,find(constructor,INJECT)!=null);}for(Method method:listedMethods(clazz)){
if(keepStereotypes){collectStereotypes(method.getDeclaredAnnotations());}boolean injection=find(method,INJECT)!=null||find(method,CDI_PRODUCES)!=null;
visitParameters(method,injection);if(find(method,CDI_PRODUCES)==null){if(find(method,PATH)!=null&&verb(method)==null){
Class<?>subResource=method.getReturnType();if(!isPlatformType(subResource)&&!subResource.isPrimitive()){
visitWithSubtypes(subResource);}}else{visitData(method.getGenericReturnType());}}}if(!clazz.isInterface()){
Set<List<Annotation>>bindingSets=bindingSets(clazz);for(Class<?>interceptor:interceptors){if(interceptor!=clazz&&matches(bindings(interceptor.
getDeclaredAnnotations()),bindingSets)){visit(interceptor);}}if(isBeanClass(clazz)){for(Class<?>decorator:decorators){
Class<?>delegateType=delegateType(decorator);if(decorator!=clazz&&delegateType!=null&&delegateType.isAssignableFrom(clazz)){
visit(decorator);}}}}}private void visitParameters(Executable executable,boolean injection){Parameter eventParameter=executable instanceof Method?
eventParameter((Method)executable):null;boolean disposer=false;for(Parameter parameter:executable.getParameters()){
disposer|=find(parameter,DISPOSES)!=null;}for(Parameter parameter:executable.getParameters()){if(keepStereotypes){
collectStereotypes(parameter.getDeclaredAnnotations());}if(parameter.equals(eventParameter)){visitData(parameter.getParameterizedType());
}else if(injection||eventParameter!=null||disposer){if(find(parameter,DISPOSES)==null){resolveInjectionPoint(parameter,parameter.getParameterizedType(
),null);}}else{visitData(parameter.getParameterizedType());}}}private void visitDataInSignature(Class<?>clazz){
if(clazz.getGenericSuperclass()instanceof ParameterizedType){for(Type argument:((ParameterizedType)clazz.getGenericSuperclass()).
getActualTypeArguments()){visitData(argument);}}for(Type genericInterface:clazz.getGenericInterfaces()){
if(genericInterface instanceof ParameterizedType){for(Type argument:((ParameterizedType)genericInterface).getActualTypeArguments()){
visitData(argument);}}}for(TypeVariable<?>parameter:clazz.getTypeParameters()){for(Type bound:parameter.getBounds()){
visitData(bound);}}}private void visitData(Type type){if(type instanceof Class){Class<?>clazz=(Class<?>)type;
while(clazz.isArray()){clazz=clazz.getComponentType();}if(isPlatformType(clazz)||known(clazz)){return;
}if(clazz.isEnum()){enums.add(clazz);return;}Class<?>superclass=clazz.getSuperclass();if(superclass!=null&&!isPlatformType(superclass)){
visitData(superclass);}if(known(clazz)){return;}dataTypes.add(clazz);visitDataInSignature(clazz);for(Field field:listedFields(clazz,true)){
visitData(field.getGenericType());}if(clazz.isInterface()||Modifier.isAbstract(clazz.getModifiers())){
for(String subtypeName:index.subtypesOf(clazz.getName())){Class<?>subtype=loadOrNull(subtypeName,clazz.getClassLoader());
if(subtype!=null&&!subtype.isAnonymousClass()&&!subtype.isSynthetic()){visitData(subtype);}}}}else if(type instanceof ParameterizedType){
visitData(((ParameterizedType)type).getRawType());for(Type argument:((ParameterizedType)type).getActualTypeArguments()){
visitData(argument);}}else if(type instanceof GenericArrayType){visitData(((GenericArrayType)type).getGenericComponentType());
}else if(type instanceof WildcardType){for(Type bound:((WildcardType)type).getUpperBounds()){visitData(bound);
}for(Type bound:((WildcardType)type).getLowerBounds()){visitData(bound);}}}private boolean known(Class<?>clazz){
return applications.contains(clazz)||resources.contains(clazz)||beans.contains(clazz)||dataTypes.contains(clazz)||enums.contains(clazz);
}private void resolveInjectionPoint(AnnotatedElement injectionPoint,Type type,String fieldName){List<Target>targets=resolve(type,qualifiers(
injectionPoint.getDeclaredAnnotations()),fieldName);Class<?>raw=rawType(type);visit(raw);if(targets==null){
return;}resolutions.put(injectionPoint,targets);for(Target target:targets){visit(target.owner);}}private List<Target>resolve(Type type,List<Annotation
>qualifiers,String fieldName){Class<?>raw=rawType(type);if(raw==null){return null;}if(isOneOf(raw,EVENT)){
return observersOf(rawType(typeArgument(type)),qualifiers);}if(isOneOf(raw,INSTANCE)||isOneOf(raw,PROVIDER)){
type=typeArgument(type);raw=rawType(type);if(raw==null){return null;}}List<Target>candidates=new ArrayList<Target>();
if(isBeanClass(raw)&&typedAllows(raw,raw)){candidates.add(new Target(raw,null));}for(String subtypeName:index.subtypesOf(raw.getName())){
Class<?>subtype=loadOrNull(subtypeName,raw.getClassLoader());if(subtype!=null&&isBeanClass(subtype)&&typedAllows(subtype,raw)){
candidates.add(new Target(subtype,null));}}for(Member producer:producers){Class<?>producedType=producer instanceof Method?((Method)producer).
getReturnType():((Field)producer).getType();if(raw.isAssignableFrom(producedType)){candidates.add(new Target(producer.getDeclaringClass(),producer));
}}List<Target>result=new ArrayList<Target>();for(Target candidate:candidates){if(qualifiersMatch(qualifiers,candidate,fieldName)){
result.add(candidate);}}result=selectAlternatives(result);if(result.isEmpty()&&isPlatformType(raw)){
return null;}return result;}private List<Target>observersOf(Class<?>eventType,List<Annotation>qualifiers){
List<Target>result=new ArrayList<Target>();if(eventType==null){return result;}for(Method observer:observers){
Parameter event=eventParameter(observer);Class<?>observedType=rawType(event.getParameterizedType());
if(observedType!=null&&observedType.isAssignableFrom(eventType)&&isBeanClass(observer.getDeclaringClass())){
boolean qualified=true;for(Annotation qualifier:qualifiers(event.getDeclaredAnnotations())){qualified&=is(qualifier,ANY)||containsEquivalent(
qualifiers,qualifier);}if(qualified){result.add(new Target(observer.getDeclaringClass(),observer));}
}}return result;}private boolean qualifiersMatch(List<Annotation>required,Target candidate,String fieldName){
AnnotatedElement element=candidate.member==null?candidate.owner:(AnnotatedElement)candidate.member;List<Annotation>available=qualifiers(cdiAnnotations
(Arrays.asList(element.getDeclaredAnnotations()),true));boolean hasDefault=find(available.toArray(new Annotation[0]),DEFAULT)!=null;
if(!hasDefault){hasDefault=true;for(Annotation qualifier:available){hasDefault&=is(qualifier,NAMED)||is(qualifier,ANY);
}}boolean onlyAny=true;for(Annotation qualifier:required){onlyAny&=is(qualifier,ANY);}if(required.isEmpty()){
return hasDefault;}if(onlyAny){return true;}for(Annotation qualifier:required){if(is(qualifier,ANY)){
continue;}if(is(qualifier,DEFAULT)){if(!hasDefault){return false;}}else if(is(qualifier,NAMED)){String requiredName=(String)value(qualifier,"value");
if(requiredName.isEmpty()){requiredName=fieldName;}Annotation named=find(available.toArray(new Annotation[0]),NAMED);
if(named==null||!Objects.equals(requiredName,beanName(named,candidate))){return false;}}else if(!containsEquivalent(available,qualifier)){
return false;}}return true;}private static String beanName(Annotation named,Target target){String name=(String)value(named,"value");
if(!name.isEmpty()){return name;}if(target.member==null){String simpleName=target.owner.getSimpleName();
return Character.toLowerCase(simpleName.charAt(0))+simpleName.substring(1);}String memberName=target.member.getName();
if(target.member instanceof Method&&memberName.startsWith("get")&&memberName.length()>3){return Character.toLowerCase(memberName.charAt(3))+memberName
.substring(4);}return memberName;}private List<Target>selectAlternatives(List<Target>candidates){List<Target>result=new ArrayList<Target>();
List<Target>alternatives=new ArrayList<Target>();int highest=Integer.MIN_VALUE;for(Target candidate:candidates){
List<Annotation>annotations=cdiAnnotations(Arrays.asList(candidate.owner.getDeclaredAnnotations()),true);
if(candidate.member!=null){annotations=new ArrayList<Annotation>(annotations);annotations.addAll(Arrays.asList(((AnnotatedElement)candidate.member).
getDeclaredAnnotations()));}Annotation[]array=annotations.toArray(new Annotation[0]);boolean alternative=find(array,ALTERNATIVE)!=null;
if(!alternative){result.add(candidate);continue;}Annotation priority=find(array,PRIORITY);if(priority!=null){
int value=(Integer)value(priority,"value");if(value>highest){alternatives.clear();highest=value;}if(value==highest){
alternatives.add(candidate);}}}return alternatives.isEmpty()?result:alternatives;}private static boolean isBeanClass(Class<?>clazz){
int modifiers=clazz.getModifiers();if(clazz.isInterface()||clazz.isEnum()||clazz.isAnnotation()||Modifier.isAbstract(modifiers)||clazz.
isAnonymousClass()||clazz.isLocalClass()||(clazz.isMemberClass()&&!Modifier.isStatic(modifiers))||isPlatformType(clazz)||find(clazz,VETOED)!=null||
find(clazz,INTERCEPTOR)!=null||find(clazz,DECORATOR)!=null){return false;}for(Constructor<?>constructor:clazz.getDeclaredConstructors()){
if(constructor.getParameterTypes().length==0||find(constructor,INJECT)!=null){return true;}}return false;
}private static boolean typedAllows(Class<?>bean,Class<?>requiredType){Annotation typed=find(bean,TYPED);
if(typed==null||requiredType==Object.class){return true;}return Arrays.asList((Class<?>[])value(typed,"value")).contains(requiredType);
}private static Class<?>delegateType(Class<?>decorator){for(Class<?>current=decorator;current!=null&&current!=Object.class;
current=current.getSuperclass()){for(Field field:current.getDeclaredFields()){if(find(field,DELEGATE)!=null){
return field.getType();}}for(Executable executable:executables(current)){for(Parameter parameter:executable.getParameters()){
if(find(parameter,DELEGATE)!=null){return parameter.getType();}}}}return null;}private static List<Executable>executables(Class<?>clazz){
List<Executable>result=new ArrayList<Executable>(Arrays.asList(clazz.getDeclaredConstructors()));result.addAll(Arrays.asList(clazz.getDeclaredMethods(
)));return result;}private static Parameter eventParameter(Method method){for(Parameter parameter:method.getParameters()){
if(find(parameter,OBSERVES)!=null||find(parameter,OBSERVES_ASYNC)!=null){return parameter;}}return null;
}private Set<List<Annotation>>bindingSets(Class<?>clazz){Set<List<Annotation>>result=new LinkedHashSet<List<Annotation>>();
List<Annotation>classBindings=bindings(clazz.getAnnotations());result.add(classBindings);for(Class<?>current=clazz;
current!=null&&current!=Object.class;current=current.getSuperclass()){for(Method method:current.getDeclaredMethods()){
List<Annotation>methodBindings=bindings(method.getDeclaredAnnotations());if(!methodBindings.isEmpty()){
List<Annotation>combined=new ArrayList<Annotation>(classBindings);combined.addAll(methodBindings);result.add(combined);
}}}return result;}private List<Annotation>bindings(Annotation[]annotations){List<Annotation>result=new ArrayList<Annotation>();
for(Annotation annotation:cdiAnnotations(Arrays.asList(annotations),true)){if(find(annotation.annotationType(),INTERCEPTOR_BINDING)!=null){
result.add(annotation);}}return result;}private static boolean matches(List<Annotation>interceptorBindings,Set<List<Annotation>>bindingSets){
if(interceptorBindings.isEmpty()){return false;}for(List<Annotation>bindings:bindingSets){boolean all=true;
for(Annotation required:interceptorBindings){all&=containsEquivalent(bindings,required);}if(all){return true;
}}return false;}private static boolean containsEquivalent(List<Annotation>annotations,Annotation required){
for(Annotation candidate:annotations){if(candidate.annotationType()!=required.annotationType()){continue;
}boolean equal=true;for(Method member:members(required)){if(find(member,NONBINDING)==null){equal&=Objects.deepEquals(value(member,candidate),value(
member,required));}}if(equal){return true;}}return false;}private List<Annotation>qualifiers(Annotation[]annotations){
List<Annotation>result=new ArrayList<Annotation>();for(Annotation annotation:annotations){if(find(annotation.annotationType(),QUALIFIER)!=null){
result.add(annotation);}}return result;}private List<Annotation>qualifiers(List<Annotation>annotations){
return qualifiers(annotations.toArray(new Annotation[0]));}private List<Annotation>cdiAnnotations(List<Annotation>annotations){
return cdiAnnotations(annotations,!keepStereotypes);}private List<Annotation>cdiAnnotations(List<Annotation>annotations,boolean expand){
if(!expand){return annotations;}List<Annotation>result=new ArrayList<Annotation>();List<Annotation>stereotypeAnnotations=new ArrayList<Annotation>();
for(Annotation annotation:annotations){if(isStereotype(annotation.annotationType())){stereotypeAnnotations.add(annotation);
}else{result.add(annotation);}}for(Annotation stereotype:stereotypeAnnotations){expand(stereotype.annotationType(),result,new LinkedHashSet<Class<?>>(
));}return result;}private void expand(Class<?>stereotype,List<Annotation>result,Set<Class<?>>visited){
if(!visited.add(stereotype)){return;}for(Annotation annotation:stereotype.getDeclaredAnnotations()){
Class<?extends Annotation>annotationType=annotation.annotationType();if(is(annotation,STEREOTYPE)){continue;
}if(isStereotype(annotationType)){expand(annotationType,result,visited);}else if(isScope(annotationType)){
if(!containsScope(result)){result.add(annotation);}}else if(renderer.isIncluded(annotation)&&!containsType(result,annotationType)){
result.add(annotation);}}}private void collectStereotypes(Annotation[]annotations){for(Annotation annotation:annotations){
Class<?extends Annotation>annotationType=annotation.annotationType();if(isStereotype(annotationType)&&stereotypes.add(annotationType)){
collectStereotypes(annotationType.getDeclaredAnnotations());}}}private static boolean isStereotype(Class<?extends Annotation>annotationType){
return find(annotationType,STEREOTYPE)!=null&&!Arrays.asList(DECORATOR).contains(annotationType.getName());
}private static boolean isScope(Class<?extends Annotation>annotationType){return find(annotationType,SCOPE)!=null||find(annotationType,NORMAL_SCOPE)!=
null;}private static boolean containsScope(List<Annotation>annotations){for(Annotation annotation:annotations){
if(isScope(annotation.annotationType())){return true;}}return false;}private static boolean containsType(List<Annotation>annotations,Class<?>
annotationType){for(Annotation annotation:annotations){if(annotation.annotationType()==annotationType){
return true;}}return false;}private boolean isResourceRelated(Class<?>clazz){Boolean result=resourceRelated.get(clazz);
if(result==null){result=hasRsAnnotation(clazz.getDeclaredAnnotations());for(Method method:clazz.getDeclaredMethods()){
result=result||hasRsAnnotation(method.getDeclaredAnnotations());}for(Class<?>supertype:supertypes(clazz)){
result=result||isResourceRelated(supertype);}resourceRelated.put(clazz,result);}return result;}private static boolean hasRsAnnotation(Annotation[]
annotations){for(Annotation annotation:annotations){if(isRs(annotation)||find(annotation.annotationType(),HTTP_METHOD)!=null){
return true;}}return false;}private static boolean isRs(Annotation annotation){String packageName=MetadataRenderer.packageName(annotation.
annotationType());return MetadataRenderer.inPackage(packageName,RS[0])||MetadataRenderer.inPackage(packageName,RS[1]);
}private static String verb(Method method){for(Annotation annotation:method.getDeclaredAnnotations()){
Annotation httpMethod=find(annotation.annotationType(),HTTP_METHOD);if(httpMethod!=null){return(String)value(httpMethod,"value");
}}return null;}private static boolean isApplication(Class<?>clazz){return superclassNamed(clazz,APPLICATION)!=null;
}private static Class<?>superclassNamed(Class<?>clazz,String[]names){for(Class<?>current=clazz.getSuperclass();
current!=null;current=current.getSuperclass()){if(Arrays.asList(names).contains(current.getName())){
return current;}}return null;}private List<Field>listedFields(Class<?>clazz,boolean dataType){List<Field>result=new ArrayList<Field>();
for(Field field:clazz.getDeclaredFields()){int modifiers=field.getModifiers();if(Modifier.isStatic(modifiers)||field.isSynthetic()||(dataType&&
Modifier.isTransient(modifiers))){continue;}if(dataType||!renderer.included(field,field.getAnnotatedType()).isEmpty()){
result.add(field);}}return result;}private List<Constructor<?>>listedConstructors(Class<?>clazz){List<Constructor<?>>result=new ArrayList<Constructor<
?>>();for(Constructor<?>constructor:clazz.getDeclaredConstructors()){if(!constructor.isSynthetic()&&isAnnotated(constructor)){
result.add(constructor);}}Collections.sort(result,new Comparator<Constructor<?>>(){public int compare(Constructor<?>a,Constructor<?>b){
return signatureKey(a).compareTo(signatureKey(b));}});return result;}private List<Method>listedMethods(Class<?>clazz){
List<Method>result=new ArrayList<Method>();for(Method method:sortedMethods(clazz)){if(!Modifier.isStatic(method.getModifiers())&&isAnnotated(method)){
result.add(method);}}return result;}private boolean isAnnotated(Executable executable){if(!renderer.included(executable).isEmpty()){
return true;}for(Parameter parameter:executable.getParameters()){if(!renderer.included(parameter,parameter.getAnnotatedType()).isEmpty()){
return true;}}return false;}private static List<Method>sortedMethods(Class<?>clazz){List<Method>result=new ArrayList<Method>();
for(Method method:clazz.getDeclaredMethods()){if(!method.isSynthetic()&&!method.isBridge()){result.add(method);
}}Collections.sort(result,new Comparator<Method>(){public int compare(Method a,Method b){return methodKey(a).compareTo(methodKey(b));
}});return result;}private static String methodKey(Method method){Annotation path=find(method,PATH);
String verb=verb(method);int verbIndex=verb==null?99:VERB_ORDER.contains(verb)?VERB_ORDER.indexOf(verb):50;
return(path==null?"":normalizePath((String)value(path,"value")))+'\u0000'+String.format("%02d",verbIndex)+(verb==null?"":verb)+'\u0000'+signatureKey(
method);}private static String signatureKey(Executable executable){StringBuilder result=new StringBuilder(executable instanceof Method?executable.
getName():"");result.append('(');for(Class<?>parameterType:executable.getParameterTypes()){result.append(TypeNames.simpleName(parameterType)).append(
',');}return result.toString();}private static List<Class<?>>supertypes(Class<?>clazz){List<Class<?>>result=new ArrayList<Class<?>>();
if(clazz.getSuperclass()!=null&&!isPlatformType(clazz.getSuperclass())){result.add(clazz.getSuperclass());
}for(Class<?>anInterface:clazz.getInterfaces()){if(!isPlatformType(anInterface)){result.add(anInterface);
}}return result;}private void writeType(Class<?>clazz,boolean dataType,StringBuilder result){if(clazz.isInterface()){
result.append('^');}else if(Modifier.isAbstract(clazz.getModifiers())){result.append('~');}result.append(name(clazz)).append(renderer.typeParameters(
clazz.getTypeParameters()));List<String>supertypes=new ArrayList<String>();Class<?>superclass=clazz.getSuperclass();
if(superclass!=null&&(known(superclass)||"java.lang.Record".equals(superclass.getName()))){supertypes.add(renderer.type(clazz.getGenericSuperclass(),
null));}Type[]genericInterfaces=clazz.getGenericInterfaces();for(int i=0;i<genericInterfaces.length;
i++){if(known(clazz.getInterfaces()[i])){supertypes.add(renderer.type(genericInterfaces[i],null));}}
for(int i=0;i<supertypes.size();i++){result.append(i==0?':':',').append(supertypes.get(i));}appendTokens(annotationTokens(cdiAnnotations(renderer.
included(clazz)),clazz),result);result.append('\n');for(Field field:listedFields(clazz,dataType)){result.append(' ').append(field.getName()).append(
' ').append(renderer.type(field.getGenericType(),field.getAnnotatedType()));appendTokens(annotationTokens(cdiAnnotations(renderer.included(field,field
.getAnnotatedType())),field),result);appendResolution(field,result);result.append('\n');}if(dataType){
return;}for(Constructor<?>constructor:listedConstructors(clazz)){result.append(" new");appendParameters(constructor,result);
appendTokens(annotationTokens(cdiAnnotations(renderer.included(constructor)),constructor),result);result.append('\n');
}for(Method method:listedMethods(clazz)){result.append(' ');String verb=verb(method);if(verb!=null){
result.append(verb).append(' ');}Annotation path=find(method,PATH);if(path!=null){result.append(path((String)value(path,"value"))).append(' ');
}result.append(method.getName());appendParameters(method,result);result.append(' ').append(renderer.type(method.getGenericReturnType(),method.
getAnnotatedReturnType()));appendTokens(annotationTokens(cdiAnnotations(renderer.included(method)),method),result);
result.append('\n');}}private void appendParameters(Executable executable,StringBuilder result){result.append('(');
Parameter[]parameters=executable.getParameters();for(int i=0;i<parameters.length;i++){Parameter parameter=parameters[i];
result.append(i>0?",":"");for(String token:annotationTokens(cdiAnnotations(renderer.included(parameter,parameter.getAnnotatedType())),parameter)){
result.append(token).append(' ');}result.append(renderer.type(parameter.getParameterizedType(),parameter.getAnnotatedType()));
appendResolution(parameter,result);}result.append(')');}private void appendResolution(AnnotatedElement injectionPoint,StringBuilder result){
List<Target>targets=resolutions.get(injectionPoint);if(targets==null){return;}Type type=injectionPoint instanceof Field?((Field)injectionPoint).
getGenericType():((Parameter)injectionPoint).getParameterizedType();Class<?>raw=rawType(type);if(raw!=null&&(isOneOf(raw,INSTANCE)||isOneOf(raw,
PROVIDER))){raw=rawType(typeArgument(type));}if(targets.size()==1&&targets.get(0).member==null&&targets.get(0).owner==raw){
return;}result.append(" =");if(targets.isEmpty()){result.append('?');}for(int i=0;i<targets.size();i++){
Target target=targets.get(i);result.append(i>0?"|":"").append(name(target.owner));if(target.member!=null){
result.append('.').append(target.member.getName());}}}private List<String>annotationTokens(List<Annotation>annotations,AnnotatedElement element){
List<String>special=new ArrayList<String>();List<String>generic=new ArrayList<String>();Annotation defaultValue=find(annotations.toArray(new Annotation
[0]),DEFAULT_VALUE);boolean hasSource=false;for(Annotation annotation:annotations){String simpleName=annotation.annotationType().getSimpleName();
if(isRs(annotation)&&PARAM_SOURCES.containsKey(simpleName)){hasSource=true;}}for(Annotation annotation:annotations){
String simpleName=annotation.annotationType().getSimpleName();if(is(annotation,PATH)||is(annotation,APPLICATION_PATH)){
if(element instanceof Class){special.add(0,path((String)value(annotation,"value")));}}else if(is(annotation,RS_PRODUCES)||is(annotation,RS_CONSUMES)){
special.add((is(annotation,RS_PRODUCES)?">":"<")+mediaTypes((String[])value(annotation,"value")));}else if(isRs(annotation)&&PARAM_SOURCES.containsKey
(simpleName)){String token=PARAM_SOURCES.get(simpleName)+":"+quoteIfNeeded((String)value(annotation,"value"),SAFE_NAME);
if(defaultValue!=null){token+="="+quoteIfNeeded((String)value(defaultValue,"value"),SAFE_NAME);}special.add(token);
}else if(is(annotation,DEFAULT_VALUE)&&hasSource){continue;}else if(find(annotation.annotationType(),HTTP_METHOD)!=null){
continue;}else{generic.add(renderer.annotation(annotation));}}special.addAll(generic);return special;
}private static final String SAFE_PATH="/{}._-~:;=@$!&*+";private static final String SAFE_NAME="._-*/+:@$!&~";
private static final String SAFE_MEDIA_TYPE="/+.*_-";static String path(String path){return quoteIfNeeded(normalizePath(path),SAFE_PATH);
}private static String normalizePath(String path){int start=0;while(start<path.length()&&path.charAt(start)=='/'){
start++;}return"/"+path.substring(start);}private static String mediaTypes(String[]mediaTypes){StringBuilder result=new StringBuilder();
for(int i=0;i<mediaTypes.length;i++){result.append(i>0?",":"").append(quoteIfNeeded(mediaTypes[i],SAFE_MEDIA_TYPE));
}return result.toString();}private static String quoteIfNeeded(String value,String safeCharacters){boolean safe=!value.isEmpty();
for(char c:value.toCharArray()){safe&=Character.isLetterOrDigit(c)||safeCharacters.indexOf(c)>=0;}return safe?value:quote(value);
}private static void appendTokens(List<String>tokens,StringBuilder result){for(String token:tokens){
result.append(' ').append(token);}}private String name(Class<?>clazz){return renderer.name(clazz);}private static Class<?>rawType(Type type){
if(type instanceof Class){return(Class<?>)type;}if(type instanceof ParameterizedType){return(Class<?>)((ParameterizedType)type).getRawType();
}return null;}private static Type typeArgument(Type type){return type instanceof ParameterizedType?((ParameterizedType)type).getActualTypeArguments()[
0]:Object.class;}private static boolean isOneOf(Class<?>clazz,String[]names){return Arrays.asList(names).contains(clazz.getName());
}private static final class Target{final Class<?>owner;final Member member;Target(Class<?>owner,Member member){
this.owner=owner;this.member=member;}}public final static class JaxRsApiGenerator extends SourceGenerator<JaxRsApiGenerator>{
private static final List<String>STANDARD_VERBS=Arrays.asList("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS");
private static final Map<Character,String>PARAM_ANNOTATIONS=new HashMap<Character,String>();static{PARAM_ANNOTATIONS.put('p',"PathParam");
PARAM_ANNOTATIONS.put('q',"QueryParam");PARAM_ANNOTATIONS.put('m',"MatrixParam");PARAM_ANNOTATIONS.put('h',"HeaderParam");
PARAM_ANNOTATIONS.put('c',"CookieParam");PARAM_ANNOTATIONS.put('f',"FormParam");}private final Set<String>customVerbs=new LinkedHashSet<String>();
private JaxRsApiGenerator(List<TypeDecl>types){super(types);}public static JaxRsApiGenerator of(String export){
return new JaxRsApiGenerator(MetadataReader.readApi(export));}public static void main(String[]args)throws IOException{
if(args.length<2){System.err.println("usage: "+JaxRsApiGenerator.class.getName()+
" <export file> <output directory> [--javax] [--package=<base package>] [<package>...]");return;}JaxRsApiGenerator generator=of(new String(Files.
readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));for(int i=2;i<args.length;i++){if("--javax".equals(args[i])){
generator.javax();}else if(args[i].startsWith("--package=")){generator.basePackage(args[i].substring("--package=".length()));
}else{generator.packages(args[i]);}}generator.writeTo(Paths.get(args[1]));}protected List<String>frameworkPackages(String namespace){
List<String>result=new ArrayList<String>();for(String suffix:new String[]{"ws.rs","ws.rs.core","ws.rs.container","ws.rs.sse","inject",
"enterprise.context","enterprise.inject","enterprise.event","enterprise.util","interceptor","decorator","annotation"}
){result.add(namespace+"."+suffix);}return result;}protected Map<String,String>frameworkMappings(String namespace){
Map<String,String>result=new HashMap<String,String>();result.put("Produces",namespace+".enterprise.inject.Produces");
return result;}protected Map<String,String>additionalSources(){Map<String,String>result=new LinkedHashMap<String,String>();
for(String verb:customVerbs){String path=basePackage().replace('.','/')+"/"+verb+".java";result.put(path,"package "+basePackage()+";\n\n"+"import "+rs
("HttpMethod")+";\n\n"+"import java.lang.annotation.ElementType;\n"+"import java.lang.annotation.Retention;\n"+
"import java.lang.annotation.RetentionPolicy;\n"+"import java.lang.annotation.Target;\n\n"+
"@Target(ElementType.METHOD)\n@Retention(RetentionPolicy.RUNTIME)\n"+"@HttpMethod("+MetadataRenderer.quote(verb)+")\n"+"public @interface "+verb+
" {\n}\n");}return result;}protected String body(TypeDecl type,SourceFile file){StringBuilder result=new StringBuilder();
Set<String>variables=variables(type.typeParameters);String typeParameters=typeParameters(type.typeParameters,file,variables);
switch(type.kind){case ENUM:result.append(metas(type.metas,file,false,"\n"));result.append("public enum ").append(type.simpleName()).append(" {\n");
result.append(type.constants.isEmpty()?"":"    "+String.join(", ",type.constants)+"\n");return result.append("}\n").toString();
case ANNOTATION:result.append('@').append(file.ref("java.lang.annotation.Retention")).append('(').append(file.ref(
"java.lang.annotation.RetentionPolicy")).append(".RUNTIME)\n");String elementType=file.ref("java.lang.annotation.ElementType");
result.append('@').append(file.ref("java.lang.annotation.Target")).append("({").append(elementType).append(".TYPE, ").append(elementType).append(
".METHOD, ").append(elementType).append(".FIELD})\n");result.append(metas(type.metas,file,false,"\n"));
return result.append("public @interface ").append(type.simpleName()).append(" {\n}\n").toString();default:break;
}TypeRef record=supertypeNamed(type,"Record");boolean application=supertypeNamed(type,"Application")!=null;
result.append(metas(type.metas,file,application,"\n"));result.append("public ");if(record!=null){result.append("record ").append(type.simpleName()).
append(typeParameters).append('(');for(int i=0;i<type.fields.size();i++){FieldDecl field=type.fields.get(i);
result.append(i>0?", ":"").append(metas(field.metas,file,false," ")).append(type(field.type,file,variables)).append(' ').append(field.name);
}result.append(')');appendSupertypes(type,record,file,variables,result);return result.append(" {\n}\n").toString();
}boolean anInterface=type.kind==Kind.INTERFACE;result.append(anInterface?"interface ":type.kind==Kind.ABSTRACT_CLASS?"abstract class ":"class ").
append(type.simpleName()).append(typeParameters);appendSupertypes(type,null,file,variables,result);result.append(" {\n");
for(FieldDecl field:type.fields){result.append('\n').append(indent(metas(field.metas,file,false,"\n"))).append("    private ").append(type(field.type,
file,variables)).append(' ').append(field.name).append(";\n");}boolean hasConstructorWithParameters=false;
boolean hasDefaultConstructor=false;for(MethodDecl method:type.methods){if(method.isConstructor()){hasConstructorWithParameters|=!method.parameters.
isEmpty();hasDefaultConstructor|=method.parameters.isEmpty();}}if(hasConstructorWithParameters&&!hasDefaultConstructor){
result.append("\n    protected ").append(type.simpleName()).append("() {\n    }\n");}for(MethodDecl method:type.methods){
result.append('\n').append(indent(verbAndPath(method,file)+metas(method.metas,file,false,"\n")));result.append(method(type,method,file,variables,new
HashMap<String,TypeRef>(),true));}if(type.kind==Kind.CLASS){for(Map.Entry<MethodDecl,Map<String,TypeRef>>inherited:unimplementedMethods(type).entrySet
()){result.append('\n').append(method(type,inherited.getKey(),file,variables,inherited.getValue(),false));
}}if(!anInterface&&type.methods.isEmpty()){for(FieldDecl field:type.fields){String fieldType=type(field.type,file,variables);
result.append("\n    public ").append(fieldType).append(" get").append(capitalize(field.name)).append("() {\n").append("        return ").append(field
.name).append(";\n    }\n\n").append("    public void set").append(capitalize(field.name)).append('(').append(fieldType).append(' ').append(field.name
).append(") {\n        this.").append(field.name).append(" = ").append(field.name).append(";\n    }\n");
}}return result.append("}\n").toString();}private String method(TypeDecl type,MethodDecl method,SourceFile file,Set<String>variables,Map<String,
TypeRef>substitution,boolean annotated){TypeRef returnType=method.returnType;StringBuilder result=new StringBuilder(annotated?"    ":
"    @Override\n    ");boolean anInterface=type.kind==Kind.INTERFACE;if(!anInterface){result.append("public ");
}if(method.isConstructor()){result.append(type.simpleName());}else{result.append(type(returnType.substitute(substitution),file,variables)).append(' ')
.append(method.name);}result.append('(');for(int i=0;i<method.parameters.size();i++){ParamDecl parameter=method.parameters.get(i);
result.append(i>0?", ":"").append(annotated?metas(parameter.metas,file,false," "):"").append(type(parameter.type.substitute(substitution),file,
variables)).append(" p").append(i);}result.append(')');if(anInterface){return result.append(";\n").toString();
}if(method.isConstructor()||"void".equals(returnType.name)&&returnType.dimensions==0){return result.append(" {\n    }\n").toString();
}return result.append(" {\n        throw new UnsupportedOperationException();\n    }\n").toString();
}private Map<MethodDecl,Map<String,TypeRef>>unimplementedMethods(TypeDecl type){Set<String>implemented=new LinkedHashSet<String>();
Map<MethodDecl,Map<String,TypeRef>>required=new LinkedHashMap<MethodDecl,Map<String,TypeRef>>();collect(type,new HashMap<String,TypeRef>(),implemented
,required,new LinkedHashSet<String>());Map<MethodDecl,Map<String,TypeRef>>result=new LinkedHashMap<MethodDecl,Map<String,TypeRef>>();
Set<String>added=new LinkedHashSet<String>();for(Map.Entry<MethodDecl,Map<String,TypeRef>>entry:required.entrySet()){
String key=key(entry.getKey());if(!implemented.contains(key)&&added.add(key)){result.put(entry.getKey(),entry.getValue());
}}return result;}private void collect(TypeDecl type,Map<String,TypeRef>substitution,Set<String>implemented,Map<MethodDecl,Map<String,TypeRef>>required
,Set<String>visited){if(!visited.add(type.name)){return;}for(MethodDecl method:type.methods){if(!method.isConstructor()){
if(type.kind==Kind.INTERFACE){required.put(method,substitution);}else{implemented.add(key(method));}
}}for(TypeRef supertype:type.supertypes){TypeDecl decl=model.get(supertype.name);if(decl!=null){Map<String,TypeRef>next=new HashMap<String,TypeRef>();
for(int i=0;i<decl.typeParameters.size()&&i<supertype.arguments.size();i++){next.put(decl.typeParameters.get(i).name,supertype.arguments.get(i).
substitute(substitution));}collect(decl,next,implemented,required,visited);}}}private static String key(MethodDecl method){
return method.name+"/"+method.parameters.size();}private void appendSupertypes(TypeDecl type,TypeRef skipped,SourceFile file,Set<String>variables,
StringBuilder result){List<String>classes=new ArrayList<String>();List<String>interfaces=new ArrayList<String>();
for(TypeRef supertype:type.supertypes){if(supertype!=skipped){(type.kind!=Kind.INTERFACE&&!isInterface(supertype)?classes:interfaces).add(type(
supertype,file,variables));}}if(!classes.isEmpty()){result.append(" extends ").append(classes.get(0));
}if(!interfaces.isEmpty()){result.append(type.kind==Kind.INTERFACE?" extends ":" implements ").append(String.join(", ",interfaces));
}}private TypeRef supertypeNamed(TypeDecl type,String name){for(TypeRef supertype:type.supertypes){if(supertype.name.equals(name)&&!model.containsKey(
name)){return supertype;}}return null;}private String verbAndPath(MethodDecl method,SourceFile file){
StringBuilder result=new StringBuilder();if(method.verb!=null){if(STANDARD_VERBS.contains(method.verb)){
result.append('@').append(file.ref(rs(method.verb))).append('\n');}else{customVerbs.add(method.verb);
result.append('@').append(file.ref(basePackage()+"."+method.verb)).append('\n');}}if(method.path!=null){
result.append('@').append(file.ref(rs("Path"))).append('(').append(MetadataRenderer.quote(method.path)).append(")\n");
}return result.toString();}private String metas(List<Meta>metas,SourceFile file,boolean application,String separator){
StringBuilder result=new StringBuilder();for(Meta meta:metas){if(meta instanceof PathMeta){result.append('@').append(file.ref(rs(application?
"ApplicationPath":"Path"))).append('(').append(MetadataRenderer.quote(((PathMeta)meta).path)).append(')').append(separator);
}else if(meta instanceof MediaTypes){MediaTypes mediaTypes=(MediaTypes)meta;result.append('@').append(file.ref(rs(mediaTypes.produces?"Produces":
"Consumes"))).append('(');result.append(mediaTypes.types.size()==1?"":"{");for(int i=0;i<mediaTypes.types.size();
i++){result.append(i>0?", ":"").append(MetadataRenderer.quote(mediaTypes.types.get(i)));}result.append(mediaTypes.types.size()==1?"":"}").append(')').
append(separator);}else if(meta instanceof ParamSource){ParamSource source=(ParamSource)meta;result.append('@').append(file.ref(rs(PARAM_ANNOTATIONS.
get(source.source)))).append('(').append(MetadataRenderer.quote(source.name)).append(')').append(separator);
if(source.defaultValue!=null){result.append('@').append(file.ref(rs("DefaultValue"))).append('(').append(MetadataRenderer.quote(source.defaultValue)).
append(')').append(separator);}}else if(meta instanceof AnnotationRef){result.append(annotation((AnnotationRef)meta,file,null)).append(separator);
}}return result.toString();}private String rs(String simpleName){return namespace()+".ws.rs."+simpleName;
}private static String indent(String lines){return lines.replaceAll("(?m)^","    ");}}}

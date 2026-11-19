// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.core;import java.io.*;import java.lang.annotation.Annotation;import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
import java.util.logging.*;import static java.nio.file.StandardOpenOption.*;public final class CallRecorder{
public static final String FILE_PROPERTY="callrecorder.file";private static final Logger LOG=Logger.getLogger(CallRecorder.class.getName());
private static volatile CallRecorder instance;private final CallRecordSink sink;private final ThreadLocal<RecordedCall>currentCall=new ThreadLocal<
RecordedCall>();private final ConcurrentMap<MethodKey,String>methodIds=new ConcurrentHashMap<MethodKey,String>();
private final Map<Class<?>,String>typeIds=new HashMap<Class<?>,String>();private final ShortNames typeNames=new ShortNames();
private final ShortNames endpointNames=new ShortNames();private int nextTypeId;private int nextMethodId;
private CallRecorder(CallRecordSink sink){this.sink=sink;}public static CallRecorder get(){CallRecorder result=instance;
if(result==null){synchronized(CallRecorder.class){result=instance;if(result==null){result=new CallRecorder(defaultSink());
instance=result;}}}return result;}public static synchronized CallRecorder install(CallRecordSink sink){
CallRecorder result=new CallRecorder(sink);instance=result;return result;}public CallRecordSink sink(){
return sink;}public Object record(Class<?>targetClass,Method method,Invocation invocation)throws Exception{
return record(targetClass,method,false,invocation);}public Object record(Class<?>targetClass,Method method,boolean detailed,Invocation invocation)
throws Exception{String methodId;try{methodId=methodIdOf(TypeNames.unproxied(targetClass),method);}catch(RuntimeException e){
LOG.log(Level.WARNING,"recording of "+method+" failed",e);return invocation.proceed();}RecordedCall parent=currentCall.get();
RecordedCall call=new RecordedCall(methodId,detailed||(parent!=null&&parent.detailed));currentCall.set(call);
try{return invocation.proceed();}catch(Throwable t){call.exception=t.getClass();throw t;}finally{if(parent==null){
currentCall.remove();writeFlow(call.complete());}else{currentCall.set(parent);parent.addChild(call.complete());
}}}private String methodIdOf(Class<?>beanClass,Method method){MethodKey key=new MethodKey(beanClass,method);
String id=methodIds.get(key);return id!=null?id:registerMethod(key);}private synchronized String registerMethod(MethodKey key){
String id=methodIds.get(key);if(id!=null){return id;}Method method=key.method;StringBuilder definition=new StringBuilder("M").append(typeIdOf(key.
beanClass)).append('.').append(method.getName()).append('(');Class<?>[]parameterTypes=method.getParameterTypes();
Annotation[][]parameterAnnotations=method.getParameterAnnotations();for(int i=0;i<parameterTypes.length;
i++){if(i>0){definition.append(',');}definition.append(MethodMetadata.observerMarker(parameterAnnotations[i])).append(typeReferenceOf(parameterTypes[i
]));}definition.append(')');MethodMetadata.Endpoint endpoint=MethodMetadata.endpoint(key.beanClass,method);
if(endpoint!=null){definition.append(' ').append(endpointNames.nameOf(endpoint.key(),endpoint.shortName()));
}id=Integer.toString(nextMethodId++,36);sink.append(definition.toString());methodIds.put(key,id);return id;
}private String typeReferenceOf(Class<?>type){StringBuilder dimensions=new StringBuilder();while(type.isArray()){
type=type.getComponentType();dimensions.append("[]");}return typeIdOf(type)+dimensions;}private synchronized String typeIdOf(Class<?>type){
String id=typeIds.get(type);if(id==null){String name=typeNames.nameOf(type,TypeNames.simpleName(type));
id=Integer.toString(nextTypeId++,36);sink.append("T"+name);typeIds.put(type,id);}return id;}private synchronized void writeFlow(RecordedCall call){
try{StringBuilder flow=new StringBuilder();serialize(call,flow);sink.append(flow.toString());}catch(RuntimeException e){
LOG.log(Level.WARNING,"writing the recorded flow failed",e);}}private void serialize(RecordedCall call,StringBuilder result){
result.append(call.methodId);if(call.recursionDepth!=1){result.append('^').append(number(call.recursionDepth));
}if(call.children!=null&&!call.children.items.isEmpty()){result.append('(');serialize(call.children,result);
result.append(')');}if(call.exception!=null){result.append('!').append(typeIdOf(TypeNames.unproxied(call.exception)));
}}private void serialize(CallSequence sequence,StringBuilder result){for(int i=0;i<sequence.items.size();
i++){if(i>0){result.append(',');}FlowItem item=sequence.items.get(i);if(item instanceof RecordedCall){
RecordedCall call=(RecordedCall)item;serialize(call,result);if(call.repeat!=1){result.append('*').append(number(call.repeat));
}}else{result.append('[');boolean first=true;for(CallLoop.Variant variant:((CallLoop)item).variants()){
if(!first){result.append('|');}first=false;serialize(variant.iteration,result);if(variant.count!=1){
result.append(':').append(number(variant.count));}if(variant.similarTo>=0){result.append('~').append(Integer.toString(variant.similarTo,36));
}}result.append(']');}}}private static String number(int value){return value==FlowItem.VARYING?"+":Integer.toString(value,36);
}private static CallRecordSink defaultSink(){Iterator<CallRecordSink>sinks=ServiceLoader.load(CallRecordSink.class).iterator();
if(sinks.hasNext()){return sinks.next();}String file=System.getProperty(FILE_PROPERTY);return file!=null?new FileCallRecordSink(Paths.get(file)):new
InMemoryCallRecordSink();}private static final class MethodKey{final Class<?>beanClass;final Method method;
MethodKey(Class<?>beanClass,Method method){this.beanClass=beanClass;this.method=method;}public boolean equals(Object o){
if(!(o instanceof MethodKey)){return false;}MethodKey other=(MethodKey)o;return beanClass==other.beanClass&&method.equals(other.method);
}public int hashCode(){return 31*beanClass.hashCode()+method.hashCode();}}final static class CallLoop extends FlowItem{
final String headMethodId;private final Map<CallSequence.ShapeKey,Variant>variants=new LinkedHashMap<CallSequence.ShapeKey,Variant>();
private CallSequence currentIteration;private int hash;CallLoop(CallSequence firstIteration,RecordedCall secondIterationStart){
this.headMethodId=secondIterationStart.methodId;firstIteration.complete();addIteration(firstIteration);
startIteration(secondIterationStart);}boolean isOpen(){return currentIteration!=null;}CallSequence currentIteration(){
return currentIteration;}void nextIteration(RecordedCall start){currentIteration.complete();addIteration(currentIteration);
startIteration(start);}List<FlowItem>close(){CallSequence last=currentIteration;currentIteration=null;
last.complete();int split=0;for(Variant variant:variants.values()){int length=variant.iteration.items.size();
if(length>split&&length<last.items.size()&&sameKeys(variant.iteration,last,length)){split=length;}}List<FlowItem>trailing=new ArrayList<FlowItem>();
if(split>0){List<FlowItem>rest=last.items.subList(split,last.items.size());trailing.addAll(rest);rest.clear();
last.complete();}addIteration(last);hash=computeHash();markSimilarVariants();return trailing;}boolean hasSimilarVariants(){
for(Variant variant:variants.values()){if(variant.similarTo>=0){return true;}}return false;}private void markSimilarVariants(){
if(variants.size()<2){return;}List<Variant>ordered=new ArrayList<Variant>(variants.values());List<List<PathSimilarity.Token>>tokens=new ArrayList<List
<PathSimilarity.Token>>();for(Variant variant:ordered){tokens.add(PathSimilarity.tokens(variant.iteration));
}for(int i=1;i<ordered.size();i++){if(tokens.get(i).size()<2){continue;}double best=PathSimilarity.THRESHOLD;
for(int j=0;j<i;j++){if(tokens.get(j).size()<2){continue;}double similarity=PathSimilarity.similarity(tokens.get(j),tokens.get(i));
if(similarity>=best&&ordered.get(i).similarTo<0||similarity>best){best=similarity;ordered.get(i).similarTo=j;
}}}}Collection<Variant>variants(){return variants.values();}private void startIteration(RecordedCall start){
currentIteration=new CallSequence();currentIteration.add(start);}private void addIteration(CallSequence iteration){
CallSequence.ShapeKey key=new CallSequence.ShapeKey(iteration);Variant variant=variants.get(key);if(variant==null){
variants.put(key,new Variant(iteration));}else{variant.count=variant.count==VARYING?VARYING:variant.count+1;
variant.iteration.mergeNumbers(iteration);}}private static boolean sameKeys(CallSequence a,CallSequence b,int length){
for(int i=0;i<length;i++){if(!a.items.get(i).key().equals(b.items.get(i).key())){return false;}}return true;
}private int computeHash(){int result=headMethodId.hashCode();for(CallSequence.ShapeKey key:variants.keySet()){
result+=key.hashCode();}return result;}int shapeHash(){return hash;}boolean sameShape(FlowItem other){
if(!(other instanceof CallLoop)){return false;}CallLoop loop=(CallLoop)other;return hash==loop.hash&&headMethodId.equals(loop.headMethodId)&&variants.
size()==loop.variants.size()&&variants.keySet().containsAll(loop.variants.keySet());}void mergeNumbers(FlowItem other){
for(Map.Entry<CallSequence.ShapeKey,Variant>entry:((CallLoop)other).variants.entrySet()){Variant variant=variants.get(entry.getKey());
variant.count=mergeNumber(variant.count,entry.getValue().count);variant.iteration.mergeNumbers(entry.getValue().iteration);
}}boolean sameNumbers(FlowItem other){for(Map.Entry<CallSequence.ShapeKey,Variant>entry:((CallLoop)other).variants.entrySet()){
Variant variant=variants.get(entry.getKey());if(variant.count!=entry.getValue().count||!variant.iteration.sameNumbers(entry.getValue().iteration)){
return false;}}return true;}String key(){return"["+headMethodId;}void collectTokens(int depth,List<PathSimilarity.Token>tokens){
for(Variant variant:variants.values()){for(FlowItem item:variant.iteration.items){item.collectTokens(depth,tokens);
}}}static final class Variant{final CallSequence iteration;int count=1;int similarTo=-1;Variant(CallSequence iteration){
this.iteration=iteration;}}}public interface CallRecordSink{void append(String line);}final static class CallSequence{
final List<FlowItem>items=new ArrayList<FlowItem>();private final boolean detailed;private int hash;
CallSequence(){this(false);}CallSequence(boolean detailed){this.detailed=detailed;}void add(RecordedCall call){
if(detailed){FlowItem last=items.isEmpty()?null:items.get(items.size()-1);if(last instanceof RecordedCall&&last.sameShape(call)&&last.sameNumbers(call
)){((RecordedCall)last).repeat++;}else{items.add(call);}return;}CallLoop openLoop=openLoop();if(openLoop!=null&&openLoop.headMethodId.equals(call.
methodId)){openLoop.nextIteration(call);return;}int plainCalls=openLoop==null?items.size():items.size()-1;
for(int i=plainCalls-1;i>=0;i--){if(((RecordedCall)items.get(i)).methodId.equals(call.methodId)){if(openLoop!=null){
closeLoop();}CallSequence firstIteration=new CallSequence();List<FlowItem>iterationItems=items.subList(i,items.size());
firstIteration.items.addAll(iterationItems);iterationItems.clear();items.add(new CallLoop(firstIteration,call));
return;}}if(openLoop!=null){openLoop.currentIteration().add(call);}else{items.add(call);}}void complete(){
if(openLoop()!=null){closeLoop();}int result=1;for(FlowItem item:items){result=31*result+item.shapeHash();
}hash=result;}int shapeHash(){return hash;}void mergeNumbers(CallSequence other){for(int i=0;i<items.size();
i++){items.get(i).mergeNumbers(other.items.get(i));}}boolean sameNumbers(CallSequence other){for(int i=0;
i<items.size();i++){FlowItem item=items.get(i);FlowItem otherItem=other.items.get(i);if(!item.sameNumbers(otherItem)||(item instanceof RecordedCall&&(
(RecordedCall)item).repeat!=((RecordedCall)otherItem).repeat)){return false;}}return true;}static boolean sameShape(CallSequence a,CallSequence b){
int size=a==null?0:a.items.size();if(size!=(b==null?0:b.items.size())){return false;}for(int i=0;i<size;
i++){if(!a.items.get(i).sameShape(b.items.get(i))){return false;}}return true;}private CallLoop openLoop(){
if(items.isEmpty()){return null;}FlowItem last=items.get(items.size()-1);return last instanceof CallLoop&&((CallLoop)last).isOpen()?(CallLoop)last:
null;}private void closeLoop(){int index=items.size()-1;CallLoop loop=(CallLoop)items.remove(index);
List<FlowItem>trailing=loop.close();boolean allDifferent=true;for(CallLoop.Variant variant:loop.variants()){
allDifferent&=variant.count==1;}if(allDifferent&&!loop.hasSimilarVariants()){for(CallLoop.Variant variant:loop.variants()){
items.addAll(variant.iteration.items);}}else if(loop.variants().size()==1&&loop.variants().iterator().next().iteration.items.size()==1){
CallLoop.Variant variant=loop.variants().iterator().next();RecordedCall call=(RecordedCall)variant.iteration.items.get(0);
call.repeat=variant.count;items.add(call);}else{items.add(loop);}items.addAll(trailing);}static final class ShapeKey{
private final CallSequence sequence;ShapeKey(CallSequence sequence){this.sequence=sequence;}public boolean equals(Object other){
return other instanceof ShapeKey&&sameShape(sequence,((ShapeKey)other).sequence);}public int hashCode(){
return sequence.hash;}}}public static class FileCallRecordSink implements CallRecordSink,Closeable{private final Path file;
private BufferedWriter writer;public FileCallRecordSink(Path file){this.file=file;}public synchronized void append(String line){
try{if(writer==null){writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,CREATE,WRITE,TRUNCATE_EXISTING);
}writer.write(line);writer.write('\n');writer.flush();}catch(IOException e){throw new UncheckedIOException(e);
}}public synchronized void close()throws IOException{if(writer!=null){writer.close();writer=null;}}}
abstract static class FlowItem{static final int VARYING=-1;abstract int shapeHash();abstract boolean sameShape(FlowItem other);
abstract void mergeNumbers(FlowItem other);abstract boolean sameNumbers(FlowItem other);abstract String key();
abstract void collectTokens(int depth,List<PathSimilarity.Token>tokens);static int mergeNumber(int a,int b){
return a==b?a:VARYING;}}public static class InMemoryCallRecordSink implements CallRecordSink{private final StringBuilder content=new StringBuilder();
public synchronized void append(String line){content.append(line).append('\n');}public synchronized String content(){
return content.toString();}public synchronized void clear(){content.setLength(0);}}@FunctionalInterface public interface Invocation{
Object proceed()throws Exception;}final static class MethodMetadata{static final String SYNC_OBSERVER_MARKER="@";
static final String ASYNC_OBSERVER_MARKER="&";private static final String OBSERVES="enterprise.event.Observes";
private static final String OBSERVES_ASYNC="enterprise.event.ObservesAsync";private static final String PATH="ws.rs.Path";
private static final String HTTP_METHOD="ws.rs.HttpMethod";private MethodMetadata(){}static String observerMarker(Annotation[]parameterAnnotations){
for(Annotation annotation:parameterAnnotations){if(is(annotation,OBSERVES)){return SYNC_OBSERVER_MARKER;
}if(is(annotation,OBSERVES_ASYNC)){return ASYNC_OBSERVER_MARKER;}}return"";}static Endpoint endpoint(Class<?>beanClass,Method method){
String classPath=null;for(Class<?>type:hierarchy(beanClass)){Annotation path=find(type.getAnnotations(),PATH);
if(path!=null){classPath=normalizePath(stringValue(path));break;}}if(classPath==null){return null;}for(Class<?>type:hierarchy(beanClass)){
Method candidate=declaredMethod(type,method);if(candidate==null){continue;}String httpMethod=null;String methodPath=null;
for(Annotation annotation:candidate.getAnnotations()){if(is(annotation,PATH)){methodPath=normalizePath(stringValue(annotation));
}else{Annotation httpMethodAnnotation=find(annotation.annotationType().getAnnotations(),HTTP_METHOD);
if(httpMethodAnnotation!=null){httpMethod=stringValue(httpMethodAnnotation);}}}if(httpMethod!=null||methodPath!=null){
return new Endpoint(httpMethod,classPath,methodPath==null?"":methodPath);}}return null;}static String normalizePath(String path){
StringBuilder withoutTemplateNames=new StringBuilder();int templateDepth=0;for(int i=0;i<path.length();
i++){char c=path.charAt(i);if(c=='{'){if(templateDepth++==0){withoutTemplateNames.append("{}");}}else if(c=='}'){
templateDepth--;}else if(templateDepth==0){withoutTemplateNames.append(c);}}StringBuilder result=new StringBuilder();
for(String segment:withoutTemplateNames.toString().split("/")){if(!segment.isEmpty()){if(result.length()>0){
result.append('/');}result.append(segment);}}return result.toString();}private static Iterable<Class<?>>hierarchy(Class<?>type){
Set<Class<?>>result=new java.util.LinkedHashSet<Class<?>>();for(Class<?>current=type;current!=null&&current!=Object.class;
current=current.getSuperclass()){result.add(current);}Deque<Class<?>>pending=new ArrayDeque<Class<?>>(result);
Set<Class<?>>interfaces=new java.util.LinkedHashSet<Class<?>>();while(!pending.isEmpty()){for(Class<?>anInterface:pending.poll().getInterfaces()){
if(interfaces.add(anInterface)){pending.add(anInterface);}}}result.addAll(interfaces);return result;
}private static Method declaredMethod(Class<?>type,Method method){try{return type.getDeclaredMethod(method.getName(),method.getParameterTypes());
}catch(NoSuchMethodException e){return null;}}private static Annotation find(Annotation[]annotations,String nameWithoutNamespace){
for(Annotation annotation:annotations){if(is(annotation,nameWithoutNamespace)){return annotation;}}return null;
}private static boolean is(Annotation annotation,String nameWithoutNamespace){String name=annotation.annotationType().getName();
return name.endsWith(nameWithoutNamespace)&&(name.equals("jakarta."+nameWithoutNamespace)||name.equals("javax."+nameWithoutNamespace));
}private static String stringValue(Annotation annotation){try{Method value=annotation.annotationType().getMethod("value");
value.setAccessible(true);return String.valueOf(value.invoke(annotation));}catch(Exception e){throw new IllegalStateException("can't read value of "+
annotation,e);}}static final class Endpoint{final String httpMethod;final String classPath;final String methodPath;
Endpoint(String httpMethod,String classPath,String methodPath){this.httpMethod=httpMethod;this.classPath=classPath;
this.methodPath=methodPath;}String shortName(){String verb=httpMethod==null?"":httpMethod;return methodPath.isEmpty()?verb:verb+'/'+methodPath;
}String key(){return httpMethod+' '+classPath+'/'+methodPath;}}}final static class PathSimilarity{static final String THRESHOLD_PROPERTY=
"callrecorder.similarity";static final double THRESHOLD=Double.parseDouble(System.getProperty(THRESHOLD_PROPERTY,"0.5"));
private static final long MAX_CELLS=1_000_000L;private PathSimilarity(){}static List<Token>tokens(CallSequence sequence){
List<Token>result=new ArrayList<Token>();for(FlowItem item:sequence.items){item.collectTokens(0,result);
}return result;}static double similarity(List<Token>a,List<Token>b){int n=a.size();int m=b.size();if((long)n*m>MAX_CELLS){
return-1;}double[]previous=new double[m+1];double[]current=new double[m+1];for(int i=1;i<=n;i++){for(int j=1;
j<=m;j++){double match=previous[j-1]+a.get(i-1).matchWeight(b.get(j-1));current[j]=Math.max(match,Math.max(previous[j],current[j-1]));
}double[]swap=previous;previous=current;current=swap;}double common=previous[m];int sharedHead=n>0&&m>0&&a.get(0).matchWeight(b.get(0))==1?1:0;
int longer=Math.max(n,m)-sharedHead;return longer<=0?1:(common-sharedHead)/longer;}static final class Token{
final int depth;final String methodId;final Class<?>exception;Token(int depth,String methodId,Class<?>exception){
this.depth=depth;this.methodId=methodId;this.exception=exception;}double matchWeight(Token other){if(depth!=other.depth||!methodId.equals(other.
methodId)){return 0;}return exception==other.exception?1:0.5;}}}final static class RecordedCall extends FlowItem{
final String methodId;final boolean detailed;Class<?>exception;int recursionDepth=1;int repeat=1;CallSequence children;
private int hash;RecordedCall(String methodId,boolean detailed){this.methodId=methodId;this.detailed=detailed;
}void addChild(RecordedCall child){if(children==null){children=new CallSequence(detailed);}children.add(child);
}RecordedCall complete(){RecordedCall result=this;if(children!=null){children.complete();if(children.items.size()==1&&children.items.get(0)instanceof
RecordedCall){RecordedCall only=(RecordedCall)children.items.get(0);if(only.repeat==1&&only.methodId.equals(methodId)&&only.exception==exception){
only.recursionDepth=only.recursionDepth==VARYING?VARYING:only.recursionDepth+recursionDepth;result=only;
}}}result.hash=result.computeHash();return result;}private int computeHash(){int result=methodId.hashCode();
result=31*result+(exception==null?0:exception.hashCode());return 31*result+(children==null?0:children.shapeHash());
}int shapeHash(){return hash;}boolean sameShape(FlowItem other){if(!(other instanceof RecordedCall)){
return false;}RecordedCall call=(RecordedCall)other;return hash==call.hash&&exception==call.exception&&methodId.equals(call.methodId)&&CallSequence.
sameShape(children,call.children);}void mergeNumbers(FlowItem other){RecordedCall call=(RecordedCall)other;
repeat=mergeNumber(repeat,call.repeat);recursionDepth=mergeNumber(recursionDepth,call.recursionDepth);
if(children!=null){children.mergeNumbers(call.children);}}boolean sameNumbers(FlowItem other){RecordedCall call=(RecordedCall)other;
return recursionDepth==call.recursionDepth&&(children==null||children.sameNumbers(call.children));}String key(){
return methodId;}void collectTokens(int depth,java.util.List<PathSimilarity.Token>tokens){tokens.add(new PathSimilarity.Token(depth,methodId,exception
));if(children!=null){for(FlowItem child:children.items){child.collectTokens(depth+1,tokens);}}}}public final static class ShortNames{
private final Map<Object,String>names=new HashMap<Object,String>();private final Map<String,Integer>usages=new HashMap<String,Integer>();
public String nameOf(Object key,String shortName){String name=names.get(key);if(name==null){Integer previous=usages.get(shortName);
int usage=previous==null?1:previous+1;name=usage==1?shortName:TypeNames.syntheticPackage(usage-1)+'.'+shortName;
usages.put(shortName,usage);names.put(key,name);}return name;}}public final static class TypeNames{private TypeNames(){
}public static Class<?>unproxied(Class<?>type){Class<?>current=type;while(needsUnwrapping(current)&&current.getSuperclass()!=null&&current.
getSuperclass()!=Object.class){current=current.getSuperclass();}return current;}public static String simpleName(Class<?>type){
String name=type.getSimpleName();if(name.isEmpty()){name=type.getName().substring(type.getName().lastIndexOf('.')+1);
}int cut=firstIndexOf(name,'$','/');return cut>0?name.substring(0,cut):name;}public static String syntheticPackage(int usage){
if(usage<1){throw new IllegalArgumentException("usage has to be >= 1");}StringBuilder result=new StringBuilder();
int n=usage;while(n>0){n--;result.append((char)('a'+n%26));n/=26;}return result.reverse().toString();
}private static boolean needsUnwrapping(Class<?>type){if(type.isSynthetic()||type.isAnonymousClass()){
return true;}String name=type.getName();return name.indexOf('/')>=0||name.contains("$$")||name.contains("$Proxy$")||name.contains("$ByteBuddy$")||name
.endsWith("_Subclass")||name.endsWith("_ClientProxy");}private static int firstIndexOf(String value,char first,char second){
int a=value.indexOf(first);int b=value.indexOf(second);return a<0?b:b<0?a:Math.min(a,b);}}}

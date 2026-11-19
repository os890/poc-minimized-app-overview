// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.format;import java.util.*;public final class CallRecording{private final List<String>types;
private final List<MethodSignature>methods;private final List<CallNode>flows;public CallRecording(List<String>types,List<MethodSignature>methods,List<
CallNode>flows){this.types=types;this.methods=methods;this.flows=flows;}public List<String>types(){return types;
}public List<MethodSignature>methods(){return methods;}public List<CallNode>flows(){return flows;}public String prettyPrint(){
StringBuilder result=new StringBuilder();for(CallNode flow:flows){print(flow,0,result);}return result.toString();
}private static void print(FlowElement element,int depth,StringBuilder result){indent(depth,result);
if(element instanceof LoopNode){result.append("loop:\n");int number=0;for(LoopNode.Variant variant:((LoopNode)element).variants()){
indent(depth+1,result);result.append("path ").append(++number).append(" x").append(FlowElement.number(variant.count()));
if(variant.similarTo()>=0){result.append(" ~path ").append(variant.similarTo()+1);}result.append(":\n");
for(FlowElement child:variant.iteration()){print(child,depth+2,result);}}return;}CallNode node=(CallNode)element;
result.append(node.method());if(node.recursionDepth()!=1){result.append(" ^").append(FlowElement.number(node.recursionDepth()));
}if(node.exceptionType()!=null){result.append(" !").append(node.exceptionType());}if(node.repeat()!=1){
result.append(" x").append(FlowElement.number(node.repeat()));}result.append('\n');for(FlowElement child:node.children()){
print(child,depth+1,result);}}private static void indent(int depth,StringBuilder result){for(int i=0;
i<depth;i++){result.append("  ");}}public final static class CallNode extends FlowElement{private final MethodSignature method;
private final List<FlowElement>children;private final String exceptionType;private final int recursionDepth;
private final int repeat;public CallNode(MethodSignature method,List<FlowElement>children,String exceptionType,int recursionDepth,int repeat){
this.method=method;this.children=children;this.exceptionType=exceptionType;this.recursionDepth=recursionDepth;
this.repeat=repeat;}public MethodSignature method(){return method;}public List<FlowElement>children(){
return children;}public String exceptionType(){return exceptionType;}public int recursionDepth(){return recursionDepth;
}public int repeat(){return repeat;}CallNode withRepeat(int newRepeat){return new CallNode(method,children,exceptionType,recursionDepth,newRepeat);
}}public final static class CallRecordingReader{private CallRecordingReader(){}public static CallRecording read(String recording){
List<String>types=new ArrayList<String>();List<MethodSignature>methods=new ArrayList<MethodSignature>();
List<CallNode>flows=new ArrayList<CallNode>();for(String line:recording.split("\n")){if(line.trim().isEmpty()){
continue;}char kind=line.charAt(0);if(kind=='T'){types.add(line.substring(1));}else if(kind=='M'){methods.add(parseMethod(line,types));
}else{flows.add(new FlowParser(line,types,methods).parseLine());}}return new CallRecording(unmodifiable(types),unmodifiable(methods),unmodifiable(
flows));}private static MethodSignature parseMethod(String line,List<String>types){int dot=line.indexOf('.');
int open=dot<0?-1:line.indexOf('(',dot);int close=open<0?-1:line.indexOf(')',open);if(close<0||(close<line.length()-1&&line.charAt(close+1)!=' ')){
throw new IllegalArgumentException("invalid method definition: "+line);}String typeName=types.get(Integer.parseInt(line.substring(1,dot),36));
String methodName=line.substring(dot+1,open);List<String>parameterTypes=new ArrayList<String>();int eventParameterIndex=-1;
String observerKind=null;String parameters=line.substring(open+1,close);if(!parameters.isEmpty()){for(String reference:parameters.split(",")){
char marker=reference.charAt(0);if(marker=='@'||marker=='&'){eventParameterIndex=parameterTypes.size();
observerKind=marker=='@'?MethodSignature.SYNC_OBSERVER:MethodSignature.ASYNC_OBSERVER;reference=reference.substring(1);
}int arrayStart=reference.indexOf('[');String id=arrayStart<0?reference:reference.substring(0,arrayStart);
String arraySuffix=arrayStart<0?"":reference.substring(arrayStart);parameterTypes.add(types.get(Integer.parseInt(id,36))+arraySuffix);
}}String endpoint=close<line.length()-1?line.substring(close+2):null;return new MethodSignature(typeName,methodName,unmodifiable(parameterTypes),
eventParameterIndex,observerKind,endpoint);}private static<T>List<T>unmodifiable(List<T>list){return Collections.unmodifiableList(new ArrayList<T>(
list));}private static final class FlowParser{private final String line;private final List<String>types;
private final List<MethodSignature>methods;private int pos;FlowParser(String line,List<String>types,List<MethodSignature>methods){
this.line=line;this.types=types;this.methods=methods;}CallNode parseLine(){CallNode result=node();if(pos!=line.length()){
throw error("unexpected trailing content");}return result;}private CallNode node(){MethodSignature method=methods.get(id());
int recursionDepth=accept('^')?number():1;List<FlowElement>children=accept('(')?sequence(')'):new ArrayList<FlowElement>();
String exceptionType=accept('!')?types.get(id()):null;return new CallNode(method,unmodifiable(children),exceptionType,recursionDepth,1);
}private List<FlowElement>sequence(char end){List<FlowElement>elements=new ArrayList<FlowElement>();
do{elements.add(element());}while(accept(','));if(end!=0){expect(end);}return elements;}private FlowElement element(){
if(accept('[')){List<LoopNode.Variant>variants=new ArrayList<LoopNode.Variant>();do{List<FlowElement>iteration=sequence((char)0);
int count=accept(':')?number():1;int similarTo=accept('~')?id():-1;variants.add(new LoopNode.Variant(unmodifiable(iteration),count,similarTo));
}while(accept('|'));expect(']');return new LoopNode(unmodifiable(variants));}CallNode node=node();return accept('*')?node.withRepeat(number()):node;
}private int number(){return accept('+')?FlowElement.VARYING:id();}private int id(){int start=pos;while(pos<line.length()&&Character.isLetterOrDigit(
line.charAt(pos))){pos++;}if(start==pos){throw error("id expected");}return Integer.parseInt(line.substring(start,pos),36);
}private boolean accept(char c){if(pos<line.length()&&line.charAt(pos)==c){pos++;return true;}return false;
}private void expect(char c){if(!accept(c)){throw error("'"+c+"' expected");}}private IllegalArgumentException error(String message){
return new IllegalArgumentException(message+" at position "+pos+" in: "+line);}}}public abstract static class FlowElement{
public static final int VARYING=-1;FlowElement(){}static String number(int value){return value==VARYING?"+":String.valueOf(value);
}}public final static class LoopNode extends FlowElement{private final List<Variant>variants;public LoopNode(List<Variant>variants){
this.variants=variants;}public List<Variant>variants(){return variants;}public static final class Variant{
private final List<FlowElement>iteration;private final int count;private final int similarTo;public Variant(List<FlowElement>iteration,int count,int
similarTo){this.iteration=iteration;this.count=count;this.similarTo=similarTo;}public int similarTo(){
return similarTo;}public List<FlowElement>iteration(){return iteration;}public int count(){return count;
}}}public final static class MethodSignature{public static final String SYNC_OBSERVER="@Observes";public static final String ASYNC_OBSERVER=
"@ObservesAsync";private final String typeName;private final String methodName;private final List<String>parameterTypes;
private final int eventParameterIndex;private final String observerKind;private final String endpoint;
public MethodSignature(String typeName,String methodName,List<String>parameterTypes,int eventParameterIndex,String observerKind,String endpoint){
this.typeName=typeName;this.methodName=methodName;this.parameterTypes=parameterTypes;this.eventParameterIndex=eventParameterIndex;
this.observerKind=observerKind;this.endpoint=endpoint;}public String typeName(){return typeName;}public String methodName(){
return methodName;}public List<String>parameterTypes(){return parameterTypes;}public boolean isObserver(){
return observerKind!=null;}public String observerKind(){return observerKind;}public int eventParameterIndex(){
return eventParameterIndex;}public boolean isEndpoint(){return endpoint!=null;}public String endpoint(){
return endpoint;}public String toString(){StringBuilder result=new StringBuilder(typeName).append('.').append(methodName).append('(');
for(int i=0;i<parameterTypes.size();i++){if(i>0){result.append(',');}if(i==eventParameterIndex){result.append(observerKind).append(' ');
}result.append(parameterTypes.get(i));}result.append(')');if(endpoint!=null){result.append(' ').append(endpoint);
}return result.toString();}}}

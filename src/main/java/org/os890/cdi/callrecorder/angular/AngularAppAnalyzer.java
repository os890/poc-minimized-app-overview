// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.angular;import java.io.*;import java.nio.charset.StandardCharsets;
import java.nio.file.Files;import java.util.*;import java.util.regex.*;import org.os890.cdi.callrecorder.analysis.MetadataModel.MetadataRenderer;
import org.os890.cdi.callrecorder.angular.AngularAppAnalyzer.Template.Element;import org.os890.cdi.callrecorder.angular.AngularAppAnalyzer.TsSource.*;
public final class AngularAppAnalyzer{private static final Set<String>ICON_TAGS=new HashSet<String>(Arrays.asList("mat-icon","i","svg","img","fa-icon"
,"ion-icon"));private static final Map<String,String>NAV_CONTAINERS=new LinkedHashMap<String,String>();
private static final Map<String,String>WIDGETS=new HashMap<String,String>();private static final Set<String>OPTION_TAGS=new HashSet<String>(Arrays.
asList("option","mat-option","mat-radio-button","mat-button-toggle","p-radioButton","ng-option","nz-option"));
private static final Set<String>IGNORED_OPERATORS=new HashSet<String>(Arrays.asList("takeUntil","takeUntilDestroyed","untilDestroyed","subscribe"));
private static final List<String>ROUTE_GUARDS=Arrays.asList("canActivate","canActivateChild","canDeactivate","canMatch","canLoad");
private static final Pattern INTERPOLATION=Pattern.compile("\\{\\{\\s*([\\w$]+)\\.([\\w$]+)\\s*}}");
static{NAV_CONTAINERS.put("mat-toolbar","toolbar");NAV_CONTAINERS.put("mat-sidenav","sidenav");NAV_CONTAINERS.put("mat-drawer","sidenav");
NAV_CONTAINERS.put("nav","nav");NAV_CONTAINERS.put("mat-nav-list","list");NAV_CONTAINERS.put("mat-tab-nav-bar","tabs");
NAV_CONTAINERS.put("p-menubar","menubar");NAV_CONTAINERS.put("p-menu","menu");NAV_CONTAINERS.put("p-panelMenu","menu");
NAV_CONTAINERS.put("p-tieredMenu","menu");NAV_CONTAINERS.put("ion-menu","sidenav");NAV_CONTAINERS.put("ion-tab-bar","tabs");
WIDGETS.put("textarea","textarea");WIDGETS.put("select","select");WIDGETS.put("mat-select","select");
WIDGETS.put("mat-radio-group","radio");WIDGETS.put("mat-checkbox","checkbox");WIDGETS.put("mat-slide-toggle","toggle");
WIDGETS.put("mat-slider","slider");WIDGETS.put("mat-button-toggle-group","toggle-group");WIDGETS.put("mat-chip-grid","chips");
WIDGETS.put("mat-chip-list","chips");WIDGETS.put("p-dropdown","select");WIDGETS.put("p-select","select");
WIDGETS.put("p-multiSelect","multiselect");WIDGETS.put("p-calendar","date");WIDGETS.put("p-datepicker","date");
WIDGETS.put("p-inputNumber","number");WIDGETS.put("p-checkbox","checkbox");WIDGETS.put("p-inputSwitch","toggle");
WIDGETS.put("p-toggleswitch","toggle");WIDGETS.put("p-autoComplete","autocomplete");WIDGETS.put("ng-select","select");
WIDGETS.put("nz-select","select");WIDGETS.put("nz-date-picker","date");WIDGETS.put("ion-input","text");
WIDGETS.put("ion-select","select");WIDGETS.put("ion-toggle","toggle");WIDGETS.put("ion-checkbox","checkbox");
WIDGETS.put("ion-textarea","textarea");}private final File directory;private boolean routeDetails;private boolean services;
private final List<TsSource>sources=new ArrayList<TsSource>();private final Map<String,ClassInfo>classes=new LinkedHashMap<String,ClassInfo>();
private final Map<String,Component>components=new LinkedHashMap<String,Component>();private final Map<String,Component>componentsBySelector=new HashMap
<String,Component>();private final Map<String,Node>variables=new LinkedHashMap<String,Node>();private final Map<String,String>variableTypes=new HashMap
<String,String>();private final Map<String,TsSource>variableSources=new HashMap<String,TsSource>();private final Map<Component,List<Out>>contents=new
HashMap<Component,List<Out>>();private final Set<Component>inProgress=new HashSet<Component>();private final Set<Component>listed=new LinkedHashSet<
Component>();private AngularAppAnalyzer(File directory){this.directory=directory;}public static String analyze(File project){
return of(project).analyze();}public static AngularAppAnalyzer of(File project){return new AngularAppAnalyzer(project);
}public AngularAppAnalyzer withRoutes(){routeDetails=true;return this;}public AngularAppAnalyzer withServices(){
services=true;return this;}public static void main(String[]args){if(args.length==0){System.err.println("usage: "+AngularAppAnalyzer.class.getName()+
" <project directory> [--routes] [--services]");return;}AngularAppAnalyzer analyzer=of(new File(args[0]));
for(int i=1;i<args.length;i++){if("--routes".equals(args[i])){analyzer.withRoutes();}else if("--services".equals(args[i])){
analyzer.withServices();}}System.out.print(analyzer.analyze());}public synchronized String analyze(){
sources.clear();classes.clear();components.clear();componentsBySelector.clear();variables.clear();variableTypes.clear();
variableSources.clear();contents.clear();listed.clear();File root=new File(directory,"src").isDirectory()?new File(directory,"src"):directory;
scan(root);StringBuilder result=new StringBuilder();Component shell=shell();if(shell!=null){listed.add(shell);
write(shell,result);}Node routes=rootRoutes();if(routes!=null){writeRoutes(routes,0,result,new HashSet<Node>());
}List<Component>pending=new ArrayList<Component>(listed);for(int i=0;i<pending.size();i++){for(Component referenced:references(pending.get(i))){
if(listed.add(referenced)){pending.add(referenced);}}}for(Component component:listed){if(component!=shell){
write(component,result);}}if(services){writeServices(result);}return result.toString();}private void scan(File directoryToScan){
File[]files=directoryToScan.listFiles();if(files==null){return;}Arrays.sort(files);for(File file:files){
String name=file.getName();if(file.isDirectory()){if(!name.equals("node_modules")&&!name.startsWith(".")&&!name.equals("dist")){
scan(file);}}else if(name.endsWith(".ts")&&!name.endsWith(".spec.ts")&&!name.endsWith(".d.ts")){TsSource source=TsSource.parse(file.getPath(),read(
file));sources.add(source);for(ClassInfo info:source.classes){classes.put(info.name,info);Decorator decorator=info.decorator("Component");
if(decorator!=null){Component component=new Component(info,file,decorator.argument());components.put(info.name,component);
if(component.selector!=null){for(String selector:component.selector.split(",")){componentsBySelector.put(selector.trim(),component);
}}}}for(Map.Entry<String,Node>variable:source.variables.entrySet()){variables.put(variable.getKey(),variable.getValue());
variableTypes.put(variable.getKey(),source.variableTypes.get(variable.getKey()));variableSources.put(variable.getKey(),source);
}}}}private static String read(File file){try{return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
}catch(IOException e){throw new IllegalArgumentException("can't read "+file,e);}}private Component shell(){
for(TsSource source:sources){for(Node statement:source.statements){final Component[]result=new Component[1];
statement.visit(new TsSource.Visitor(){public void visit(Node node){if(result[0]==null&&node.kind==Node.Kind.CHAIN&&node.segments.size()>0&&
"bootstrapApplication".equals(node.segments.get(0).name)&&node.segments.get(0).arguments!=null&&!node.segments.get(0).arguments.isEmpty()){
result[0]=components.get(node.segments.get(0).arguments.get(0).path());}}});if(result[0]!=null){return result[0];
}}}for(ClassInfo info:classes.values()){Decorator module=info.decorator("NgModule");if(module!=null&&module.argument()!=null&&module.argument().
properties.containsKey("bootstrap")){Node bootstrap=module.argument().properties.get("bootstrap");if(!bootstrap.items.isEmpty()){
return components.get(bootstrap.items.get(0).path());}}}return componentsBySelector.get("app-root");
}private Node rootRoutes(){final List<Node>found=new ArrayList<Node>();TsSource.Visitor visitor=new TsSource.Visitor(){
public void visit(Node node){if(node.isCall()&&found.isEmpty()){String path=node.path();if((path.equals("provideRouter")||path.endsWith(
"RouterModule.forRoot"))&&!node.last().arguments.isEmpty()){found.add(node.last().arguments.get(0));
}}}};for(TsSource source:sources){for(Node variable:source.variables.values()){variable.visit(visitor);
}for(Node statement:source.statements){statement.visit(visitor);}for(ClassInfo info:source.classes){
for(Decorator decorator:info.decorators){for(Node argument:decorator.arguments){argument.visit(visitor);
}}}}if(!found.isEmpty()){return resolve(found.get(0));}for(Map.Entry<String,String>type:variableTypes.entrySet()){
if(type.getValue()!=null&&type.getValue().equals("Routes")){return variables.get(type.getKey());}}return null;
}private Node resolve(Node node){if(node!=null&&node.kind==Node.Kind.CHAIN&&node.segments.size()==1&&node.segments.get(0).arguments==null&&variables.
containsKey(node.path())){return variables.get(node.path());}return node;}private void writeRoutes(Node routes,int depth,StringBuilder result,Set<Node
>visited){routes=resolve(routes);if(routes==null||routes.kind!=Node.Kind.ARRAY||!visited.add(routes)){
return;}for(Node route:routes.items){route=resolve(route);if(route.kind!=Node.Kind.OBJECT){continue;
}Map<String,Node>properties=route.properties;String path=string(properties.get("path"));String component=properties.containsKey("component")?
properties.get("component").path():null;Node children=properties.get("children");boolean lazy=false;
if(properties.containsKey("loadComponent")){component=lazyTarget(properties.get("loadComponent"));lazy=true;
}if(properties.containsKey("loadChildren")){String target=lazyTarget(properties.get("loadChildren"));
children=target==null?null:childRoutes(target);lazy=true;}if(component==null&&children==null&&!routeDetails){
continue;}StringBuilder line=new StringBuilder();for(int i=0;i<depth;i++){line.append(' ');}line.append('/').append(path==null?"":path);
if(component!=null){line.append(' ').append(component);Component page=components.get(component);if(page!=null){
listed.add(page);}}if(routeDetails){if(properties.containsKey("redirectTo")){String redirect=string(properties.get("redirectTo"));
line.append(" =").append(redirect==null?"?":redirect.startsWith("/")?redirect:"/"+redirect);}if(properties.containsKey("pathMatch")){
line.append(" pathMatch=").append(string(properties.get("pathMatch")));}if(lazy){line.append(" lazy");
}for(String guard:ROUTE_GUARDS){if(properties.containsKey(guard)){line.append(' ').append(guard).append('=').append(names(properties.get(guard)));
}}if(properties.containsKey("resolve")&&properties.get("resolve").kind==Node.Kind.OBJECT){List<String>resolvers=new ArrayList<String>();
for(Map.Entry<String,Node>resolver:properties.get("resolve").properties.entrySet()){resolvers.add(resolver.getKey()+":"+resolver.getValue().path());
}line.append(" resolve=").append(join(resolvers,","));}if(properties.containsKey("title")){String title=string(properties.get("title"));
line.append(" title=").append(title!=null?MetadataRenderer.quote(title):properties.get("title").path());
}if(properties.containsKey("outlet")){line.append(" outlet=").append(string(properties.get("outlet")));
}if(properties.containsKey("data")){line.append(" data=").append(compact(properties.get("data")));}}
result.append(line).append('\n');if(children!=null){writeRoutes(children,depth+1,result,visited);}}}
private static String lazyTarget(Node loader){final String[]result=new String[1];loader.visit(new TsSource.Visitor(){
public void visit(Node node){if(node.kind==Node.Kind.ARROW&&node.parameters.size()==1&&!node.items.isEmpty()&&node.items.get(0).kind==Node.Kind.CHAIN)
{Node body=node.items.get(0);if(body.segments.size()>=2&&node.parameters.get(0).equals(body.segments.get(0).name)){
result[0]=body.segments.get(body.segments.size()-1).name;}}}});return result[0];}private Node childRoutes(String target){
if(variables.containsKey(target)){return variables.get(target);}ClassInfo module=classes.get(target);
if(module!=null){final Node[]found=new Node[1];for(Decorator decorator:module.decorators){for(Node argument:decorator.arguments){
argument.visit(new TsSource.Visitor(){public void visit(Node node){if(found[0]==null&&node.isCall()&&node.path().endsWith("RouterModule.forChild")&&!
node.last().arguments.isEmpty()){found[0]=node.last().arguments.get(0);}}});}}return found[0];}return null;
}private List<Out>content(Component component){List<Out>result=contents.get(component);if(result!=null){
return result;}if(!inProgress.add(component)){return Collections.emptyList();}try{result=new Analysis(component).run();
contents.put(component,result);return result;}finally{inProgress.remove(component);}}private boolean isRelevant(Component component){
return!content(component).isEmpty();}private List<Component>references(Component component){List<Component>result=new ArrayList<Component>();
collectReferences(content(component),result);return result;}private void collectReferences(List<Out>nodes,List<Component>result){
for(Out node:nodes){if(node.reference!=null&&isRelevant(node.reference)){result.add(node.reference);
}collectReferences(node.children,result);}}private void write(Component component,StringBuilder result){
List<Out>content=content(component);if(content.isEmpty()){return;}result.append(component.info.name).append('\n');
for(Out node:content){node.write(1,result);}}private void writeServices(StringBuilder result){Map<String,ClassInfo>injectables=new TreeMap<String,
ClassInfo>();for(ClassInfo info:classes.values()){if(info.decorator("Injectable")!=null){injectables.put(info.name,info);
}}for(ClassInfo info:injectables.values()){result.append(info.name);Node options=info.decorator("Injectable").argument();
if(options!=null&&options.properties.containsKey("providedIn")){String providedIn=string(options.properties.get("providedIn"));
result.append(' ').append(providedIn!=null?providedIn:options.properties.get("providedIn").path());}
result.append('\n');List<String>injected=injected(info,true);if(!injected.isEmpty()){result.append(" inject ").append(join(injected,",")).append('\n')
;}for(Member member:info.members){if(member.isPrivate||member.isStatic||member.name.startsWith("#")||member.name.equals("constructor")){
continue;}List<String>calls=httpCalls(member.method?member.body:member.initializer,info);if(member.method&&!member.getter){
List<String>parameters=new ArrayList<String>();for(Param parameter:member.parameters){parameters.add(parameter.name);
}result.append(' ').append(member.name).append('(').append(join(parameters,",")).append(')');String type=member.type!=null?member.type:inferredType(
member.body,calls);if(type!=null){result.append(' ').append(type);}}else{String type=member.type!=null?member.type:inferredType(member.initializer,
calls);if(type==null||!type.matches(".*(Observable|Subject|Signal|signal|EventEmitter).*")){continue;
}result.append(' ').append(member.name).append(' ').append(type);}for(String call:calls){result.append(' ').append(call);
}result.append('\n');}}Map<String,String>functions=new TreeMap<String,String>();for(Map.Entry<String,String>variable:variableTypes.entrySet()){
String type=variable.getValue();if(type!=null&&type.matches("(CanActivate|CanActivateChild|CanDeactivate|CanMatch|Resolve|HttpInterceptor)Fn.*")){
StringBuilder line=new StringBuilder(variable.getKey()).append(' ').append(type).append('\n');List<String>injected=injectCalls(variables.get(variable.
getKey()),true);if(!injected.isEmpty()){line.append(" inject ").append(join(injected,",")).append('\n');
}functions.put(variable.getKey(),line.toString());}}for(String function:functions.values()){result.append(function);
}}private List<String>injected(ClassInfo info,boolean includeHttp){Set<String>result=new LinkedHashSet<String>();
for(Member member:info.members){if(member.initializer!=null){result.addAll(injectCalls(member.initializer,includeHttp));
}}for(Param parameter:info.constructorParameters){String type=parameter.type==null?null:parameter.type.replaceAll("<.*","");
if(type!=null&&(classes.containsKey(type)&&classes.get(type).decorator("Component")==null||includeHttp&&"HttpClient".equals(type))){
result.add(type);}}return new ArrayList<String>(result);}private List<String>injectCalls(Node node,final boolean includeHttp){
final Set<String>result=new LinkedHashSet<String>();if(node==null){return new ArrayList<String>();}node.visit(new TsSource.Visitor(){
public void visit(Node candidate){if(candidate.kind==Node.Kind.CHAIN&&!candidate.segments.isEmpty()&&"inject".equals(candidate.segments.get(0).name)&&
candidate.segments.get(0).arguments!=null&&!candidate.segments.get(0).arguments.isEmpty()){String type=candidate.segments.get(0).arguments.get(0).path
();if(classes.containsKey(type)&&classes.get(type).decorator("Component")==null||includeHttp&&("HttpClient".equals(type)||"Router".equals(type))){
result.add(type);}}}});return new ArrayList<String>(result);}private List<String>httpCalls(Node node,final ClassInfo owner){
final List<String>result=new ArrayList<String>();if(node==null){return result;}node.visit(new TsSource.Visitor(){
public void visit(Node candidate){if(candidate.kind!=Node.Kind.CHAIN){return;}for(int i=1;i<candidate.segments.size();
i++){Segment segment=candidate.segments.get(i);String receiver=candidate.segments.get(i-1).name;if(segment.arguments!=null&&receiver!=null&&receiver.
toLowerCase().contains("http")&&Arrays.asList("get","post","put","patch","delete","head","options","request").contains(segment.name)&&!segment.
arguments.isEmpty()){result.add(segment.name.toUpperCase()+" "+url(segment.arguments.get(0),owner));
}}}});return result;}private String url(Node node,ClassInfo owner){if(node.kind==Node.Kind.STRING){return node.text;
}if(node.kind==Node.Kind.TEMPLATE){StringBuilder result=new StringBuilder();Matcher matcher=Pattern.compile("\\$\\{([^}]*)}").matcher(node.text);
int last=0;while(matcher.find()){result.append(node.text,last,matcher.start());String value=constant(TsSource.parseExpression(matcher.group(1)),owner)
;result.append(value!=null?value:"{}");last=matcher.end();}return result.append(node.text.substring(last)).toString();
}if(node.kind==Node.Kind.OTHER&&"+".equals(node.text)){StringBuilder result=new StringBuilder();for(Node part:node.items){
String value=constant(part,owner);result.append(value!=null?value:"{}");}return result.toString();}String value=constant(node,owner);
return value!=null?value:"{}";}private String constant(Node node,ClassInfo owner){if(node==null){return null;
}if(node.kind==Node.Kind.STRING){return node.text;}if(node.kind!=Node.Kind.CHAIN||node.segments.isEmpty()){
return null;}List<Segment>segments=node.segments;if("this".equals(segments.get(0).name)&&segments.size()==2&&owner!=null){
Member member=owner.member(segments.get(1).name);return member==null||member.initializer==null?null:constant(member.initializer,owner);
}Node current=variables.get(segments.get(0).name);for(int i=1;i<segments.size()&&current!=null;i++){
current=current.kind==Node.Kind.OBJECT?current.properties.get(segments.get(i).name):null;}return current!=null&&current.kind==Node.Kind.STRING?current
.text:null;}private static String inferredType(Node node,List<String>httpCalls){if(node==null){return null;
}if(node.kind==Node.Kind.NEW){return node.text+(node.typeArguments==null?"":node.typeArguments);}final String[]result=new String[1];
node.visit(new TsSource.Visitor(){public void visit(Node candidate){if(result[0]!=null||candidate.kind!=Node.Kind.CHAIN){
return;}for(Segment segment:candidate.segments){if(segment.arguments==null||segment.name==null){continue;
}if(Arrays.asList("get","post","put","patch","delete").contains(segment.name)&&segment.typeArguments!=null){
result[0]="Observable"+segment.typeArguments;}else if(Arrays.asList("signal","computed","toSignal","input","model").contains(segment.name)){
result[0]="signal";}else if("asObservable".equals(segment.name)||"pipe".equals(segment.name)){result[0]="Observable";
}}}});return result[0];}private static String string(Node node){return node==null?null:node.kind==Node.Kind.STRING||node.kind==Node.Kind.TEMPLATE?node
.text:null;}private static String names(Node node){List<String>result=new ArrayList<String>();for(Node item:node.kind==Node.Kind.ARRAY?node.items:
Collections.singletonList(node)){result.add(item.isCall()?item.path()+"()":item.path());}return join(result,",");
}static String compact(Node node){switch(node.kind){case STRING:return MetadataRenderer.quote(node.text);
case NUMBER:case KEYWORD:case REGEX:return node.text;case OBJECT:List<String>properties=new ArrayList<String>();
for(Map.Entry<String,Node>property:node.properties.entrySet()){properties.add(property.getKey()+":"+compact(property.getValue()));
}return"{"+join(properties,",")+"}";case ARRAY:List<String>items=new ArrayList<String>();for(Node item:node.items){
items.add(compact(item));}return"["+join(items,",")+"]";case CHAIN:StringBuilder result=new StringBuilder();
for(Segment segment:node.segments){if(segment.name!=null&&!"this".equals(segment.name)){result.append(result.length()>0?".":"").append(segment.name);
}if(segment.arguments!=null){List<String>arguments=new ArrayList<String>();for(Node argument:segment.arguments){
arguments.add(compact(argument));}result.append('(').append(join(arguments,",")).append(')');}}return result.toString();
default:return"?";}}static String join(List<String>values,String separator){StringBuilder result=new StringBuilder();
for(String value:values){result.append(result.length()>0?separator:"").append(value);}return result.toString();
}static String quoteIfNeeded(String value){return value.matches("[\\w$.\\-:/@*]+")?value:MetadataRenderer.quote(value);
}final class Component{final ClassInfo info;final File file;final String selector;final Element template;
Component(ClassInfo info,File file,Node metadata){this.info=info;this.file=file;String templateText=null;
String selectorValue=null;if(metadata!=null){selectorValue=string(metadata.properties.get("selector"));
templateText=string(metadata.properties.get("template"));String templateUrl=string(metadata.properties.get("templateUrl"));
if(templateUrl!=null){File templateFile=new File(file.getParentFile(),templateUrl);templateText=templateFile.isFile()?read(templateFile):null;
}}this.selector=selectorValue;this.template=templateText==null?null:Template.parse(templateText);}}static final class Out{
final String line;final List<Out>children=new ArrayList<Out>();Component reference;FormModel form;Control control;
Out(String line){this.line=line;}boolean isEmpty(){return line==null&&children.isEmpty();}void write(int depth,StringBuilder result){
if(line!=null){indent(depth,result);result.append(line).append('\n');}for(Out child:children){child.write(line==null?depth:depth+1,result);
}}static void indent(int depth,StringBuilder result){for(int i=0;i<depth;i++){result.append(' ');}}}
static final class FormModel{final String name;final boolean templateDriven;String submit;final Control root=new Control(null,"group");
boolean placed;FormModel(String name,boolean templateDriven){this.name=name;this.templateDriven=templateDriven;
}}static final class Control{final String name;String kind;final Map<String,Control>children=new LinkedHashMap<String,Control>();
Control element;final List<String>validators=new ArrayList<String>();final List<String>reactive=new ArrayList<String>();
String widget;String options;boolean disabled;Control(String name,String kind){this.name=name;this.kind=kind;
}Control child(String childName,String childKind){Control result=children.get(childName);if(result==null){
result=new Control(childName,childKind);children.put(childName,result);}return result;}void addValidator(String validator){
String validatorName=validator.replaceAll("\\(.*","");for(String existing:validators){if(existing.replaceAll("\\(.*","").equals(validatorName)){
return;}}validators.add(validator);}void write(String label,int depth,StringBuilder result){Out.indent(depth,result);
result.append(label);result.append(' ').append("control".equals(kind)?(widget==null?"?":widget):kind);
for(String validator:validators){result.append(' ').append(validator);}if(disabled){result.append(" disabled");
}if(options!=null){result.append(' ').append(options);}for(String token:reactive){result.append(' ').append(token);
}result.append('\n');for(Control child:children.values()){child.write(quoteIfNeeded(child.name),depth+1,result);
}if(element!=null){element.write("[]",depth+1,result);}}}private final class Analysis{private final Component component;
private final ClassInfo info;private final Map<String,FormModel>forms=new LinkedHashMap<String,FormModel>();
private final Map<String,Control>standaloneControls=new LinkedHashMap<String,Control>();private final Map<String,Element>references=new HashMap<String
,Element>();Analysis(Component component){this.component=component;this.info=component.info;}List<Out>run(){
collectTsForms();Out root=new Out(null);if(services){List<String>injected=injected(info,false);if(!injected.isEmpty()){
root.children.add(new Out("inject "+join(injected,",")));}}if(component.template!=null){collectReferences(component.template);
walk(component.template,new Context(),root);}for(FormModel form:forms.values()){if(!form.placed){Out out=new Out(null);
out.form=form;root.children.add(out);}}for(Control control:standaloneControls.values()){if(control.widget==null&&!control.reactive.isEmpty()){
Out out=new Out(null);out.control=control;root.children.add(out);}}for(String[]dialog:dialogs()){Out out=new Out("dialog "+dialog[0]+(dialog[1]==null?
"":" "+dialog[1]+"()"));out.reference=components.get(dialog[0]);root.children.add(out);}return finish(root.children);
}private List<Out>finish(List<Out>nodes){List<Out>result=new ArrayList<Out>();for(Out node:nodes){List<Out>children=finish(node.children);
if(node.form!=null){Out rendered=renderForm(node.form,children);if(rendered!=null){result.add(rendered);
}continue;}if(node.control!=null){StringBuilder line=new StringBuilder();node.control.write("control "+quoteIfNeeded(node.control.name),0,line);
result.add(new Out(line.toString().trim()));continue;}if(node.reference!=null&&node.line.startsWith("+")&&!isRelevant(node.reference)){
continue;}Out copy=new Out(node.line);copy.reference=node.reference;copy.children.addAll(children);boolean container=node.line!=null&&(node.line.
equals("tabs")||node.line.startsWith("steps")||node.line.startsWith("nav "));if(container&&children.isEmpty()||node.line==null&&children.isEmpty()){
continue;}if(node.line==null){result.addAll(children);}else{result.add(copy);}}return result;}private Out renderForm(FormModel form,List<Out>nested){
if(form.root.children.isEmpty()&&form.submit==null&&nested.isEmpty()){return null;}StringBuilder line=new StringBuilder("form ").append(quoteIfNeeded(
form.name));if(form.templateDriven){line.append(" ngForm");}if(form.submit!=null){line.append(" submit=").append(form.submit);
}for(String validator:form.root.validators){line.append(' ').append(validator);}Out result=new Out(line.toString());
StringBuilder fields=new StringBuilder();for(Control control:form.root.children.values()){control.write(quoteIfNeeded(control.name),0,fields);
}for(String field:fields.toString().split("\n")){if(!field.isEmpty()){result.children.add(new Out(field));
}}result.children.addAll(nested);return result;}private void collectTsForms(){List<Member>members=new ArrayList<Member>(info.members);
for(Member member:members){if(!member.method&&member.initializer!=null){register(member.name,formExpression(member.initializer));
}}List<Node>bodies=bodies();for(Node body:bodies){body.visit(new TsSource.Visitor(){public void visit(Node node){
if(node.kind==Node.Kind.ASSIGN&&"=".equals(node.text)&&node.target.kind==Node.Kind.CHAIN&&node.target.segments.size()==2&&"this".equals(node.target.
segments.get(0).name)){register(node.target.segments.get(1).name,formExpression(node.value));}}});}for(Node body:bodies){
body.visit(new TsSource.Visitor(){public void visit(Node node){if(node.kind!=Node.Kind.CHAIN){return;
}for(int i=1;i<node.segments.size();i++){Segment segment=node.segments.get(i);if("push".equals(segment.name)&&segment.arguments!=null&&segment.
arguments.size()==1){Node element=formExpression(segment.arguments.get(0));Control array=control(node.segments.subList(0,i),false);
if(element!=null&&array!=null&&"array".equals(array.kind)&&array.element==null){array.element=control(null,element);
}}}}});}for(Member member:members){if(!member.method&&member.initializer!=null){valueChanges(member.initializer,member.name);
}}for(Node body:bodies){body.visit(new TsSource.Visitor(){public void visit(Node node){if(node.kind==Node.Kind.ASSIGN&&node.target.kind==Node.Kind.
CHAIN){valueChanges(node.value,node.target.last().name);}else if(node.kind==Node.Kind.CHAIN&&hasSegment(node,"subscribe")&&hasSegment(node,
"valueChanges")){valueChanges(node,null);}}});}}private List<Node>bodies(){List<Node>result=new ArrayList<Node>();
if(info.constructorBody!=null){result.add(info.constructorBody);}for(Member member:info.members){if(member.body!=null&&!member.getter){
result.add(member.body);}}return result;}private void register(String name,Node expression){if(expression==null){
return;}Control control=control(name,expression);if("control".equals(control.kind)){standaloneControls.put(name,control);
}else if(!forms.containsKey(name)){FormModel form=new FormModel(name,false);form.root.kind=control.kind;
form.root.children.putAll(control.children);form.root.validators.addAll(control.validators);form.root.element=control.element;
forms.put(name,form);}}private Node formExpression(Node node){if(node==null){return null;}if(formKind(node)!=null){
return node;}if(node.isCall()&&node.segments.size()==2&&"this".equals(node.segments.get(0).name)){Member method=info.member(node.segments.get(1).name)
;if(method!=null&&method.body!=null){final Node[]found=new Node[1];method.body.visit(new TsSource.Visitor(){
public void visit(Node candidate){if(found[0]==null&&formKind(candidate)!=null){found[0]=candidate;}
}});return found[0];}}return null;}private String formKind(Node node){if(node.kind==Node.Kind.NEW){String type=node.text.replace("Untyped","");
return type.equals("FormGroup")||type.equals("FormRecord")?"group":type.equals("FormArray")?"array":type.equals("FormControl")?"control":null;
}if(node.isCall()&&node.segments.size()>=2){String name=node.last().name;String receiver=node.segments.get(node.segments.size()-2).name;
boolean builder=receiver!=null&&(receiver.toLowerCase().contains("fb")||receiver.toLowerCase().contains("builder")||receiver.equals("nonNullable"));
if(builder){return"group".equals(name)||"record".equals(name)?"group":"array".equals(name)?"array":"control".equals(name)?"control":null;
}}return null;}private Control control(String name,Node construct){String kind=formKind(construct);Control result=new Control(name,kind);
List<Node>arguments=construct.kind==Node.Kind.NEW?construct.items:construct.last().arguments;Node first=arguments.isEmpty()?null:arguments.get(0);
Node second=arguments.size()>1?arguments.get(1):null;Node third=arguments.size()>2?arguments.get(2):null;
if("group".equals(kind)){if(first!=null&&first.kind==Node.Kind.OBJECT){for(Map.Entry<String,Node>entry:first.properties.entrySet()){
result.children.put(unquote(entry.getKey()),spec(unquote(entry.getKey()),entry.getValue()));}}options(result,second,third);
}else if("array".equals(kind)){if(first!=null&&first.kind==Node.Kind.ARRAY&&!first.items.isEmpty()){
result.element=spec(null,first.items.get(0));}options(result,second,third);}else{if(first!=null&&first.kind==Node.Kind.OBJECT&&first.properties.
containsKey("disabled")&&"true".equals(first.properties.get("disabled").text)){result.disabled=true;
}options(result,second,third);}return result;}private Control spec(String name,Node value){Node construct=formExpression(value);
if(construct!=null){return control(name,construct);}Control result=new Control(name,"control");if(value.kind==Node.Kind.ARRAY){
if(!value.items.isEmpty()&&value.items.get(0).kind==Node.Kind.OBJECT){Node disabled=value.items.get(0).properties.get("disabled");
result.disabled=disabled!=null&&"true".equals(disabled.text);}options(result,value.items.size()>1?value.items.get(1):null,value.items.size()>2?value.
items.get(2):null);}return result;}private void options(Control control,Node validatorsOrOptions,Node asyncValidators){
if(validatorsOrOptions!=null&&validatorsOrOptions.kind==Node.Kind.OBJECT){Map<String,Node>properties=validatorsOrOptions.properties;
validators(control,properties.get("validators"),false);validators(control,properties.get("asyncValidators"),true);
if(properties.containsKey("updateOn")){control.validators.add("updateOn("+string(properties.get("updateOn"))+")");
}}else{validators(control,validatorsOrOptions,false);validators(control,asyncValidators,true);}}private void validators(Control control,Node node,
boolean async){if(node==null||node.kind==Node.Kind.KEYWORD){return;}if(node.kind==Node.Kind.ARRAY){for(Node item:node.items){
validators(control,item,async);}return;}if(node.isCall()&&"compose".equals(node.last().name)||node.isCall()&&"composeAsync".equals(node.last().name)){
for(Node argument:node.last().arguments){validators(control,argument,async);}return;}if(node.kind!=Node.Kind.CHAIN||node.segments.isEmpty()){
return;}Segment last=node.last();StringBuilder validator=new StringBuilder(last.name==null?"?":last.name);
if(last.arguments!=null){List<String>arguments=new ArrayList<String>();for(Node argument:last.arguments){
arguments.add(compact(argument));}validator.append('(').append(join(arguments,",")).append(')');}control.addValidator(async?"async("+validator+")":
validator.toString());}private Control control(List<Segment>segments,boolean create){List<String>path=new ArrayList<String>();
String formName=null;for(int i=0;i<segments.size();i++){Segment segment=segments.get(i);if("this".equals(segment.name)){
continue;}if(formName==null){formName=segment.name;if(!forms.containsKey(formName)&&!standaloneControls.containsKey(formName)){
Member getter=info.member(formName);if(getter!=null&&getter.getter&&getter.body!=null){return getterControl(getter,create);
}}continue;}if("get".equals(segment.name)&&segment.arguments!=null&&!segment.arguments.isEmpty()){String value=string(segment.arguments.get(0));
if(value!=null){path.addAll(Arrays.asList(value.split("\\.")));}}else if("controls".equals(segment.name)){
continue;}else if(segment.name==null&&segment.index!=null&&string(segment.index)!=null){path.add(string(segment.index));
}else if(segment.name!=null&&segment.arguments==null&&i>0&&"controls".equals(segments.get(i-1).name)){
path.add(segment.name);}else{break;}}if(formName==null){return null;}if(standaloneControls.containsKey(formName)&&path.isEmpty()){
return standaloneControls.get(formName);}FormModel form=forms.get(formName);if(form==null){return null;
}Control current=form.root;for(String name:path){Control next=current.children.get(name);if(next==null){
if(!create){return null;}next=current.child(name,"control");}current=next;}return current;}private Control getterControl(Member getter,final boolean
create){final Control[]result=new Control[1];getter.body.visit(new TsSource.Visitor(){public void visit(Node node){
if(result[0]==null&&node.kind==Node.Kind.CHAIN&&node.segments.size()>1&&"this".equals(node.segments.get(0).name)){
result[0]=control(node.segments,create);}}});return result[0];}private void valueChanges(Node expression,final String target){
expression.visit(new TsSource.Visitor(){public void visit(Node node){if(node.kind!=Node.Kind.CHAIN){
return;}int index=-1;for(int i=0;i<node.segments.size();i++){if("valueChanges".equals(node.segments.get(i).name)){
index=i;}}if(index<0){return;}Control control=control(node.segments.subList(0,index),false);if(control==null){
return;}List<String>operators=new ArrayList<String>();Set<String>targets=new LinkedHashSet<String>();
if(target!=null){targets.add(target);}for(int i=index+1;i<node.segments.size();i++){Segment segment=node.segments.get(i);
if("pipe".equals(segment.name)&&segment.arguments!=null){for(Node operator:segment.arguments){String name=operator.isCall()?operator.last().name:
operator.path();if(name!=null&&!IGNORED_OPERATORS.contains(name)){operators.add(name);}}}else if("subscribe".equals(segment.name)&&segment.arguments!=
null&&target==null){for(Node callback:segment.arguments){targets.addAll(affected(callback));}}}String token="~"+join(operators,",")+(targets.isEmpty()
?"":">"+join(new ArrayList<String>(targets),","));if(!control.reactive.contains(token)){control.reactive.add(token);
}}});}private Set<String>affected(Node callback){final Set<String>result=new LinkedHashSet<String>();
callback.visit(new TsSource.Visitor(){public void visit(Node node){if(node.kind!=Node.Kind.CHAIN||node.segments.size()<2||!"this".equals(node.segments
.get(0).name)){return;}List<String>path=new ArrayList<String>();for(int i=2;i<node.segments.size();i++){
Segment segment=node.segments.get(i);if("get".equals(segment.name)&&segment.arguments!=null&&!segment.arguments.isEmpty()&&string(segment.arguments.
get(0))!=null){path.add(string(segment.arguments.get(0)));}else if(i>2&&"controls".equals(node.segments.get(i-1).name)&&segment.name!=null){
path.add(segment.name);}}if(!path.isEmpty()&&forms.containsKey(node.segments.get(1).name)){result.add(join(path,"."));
}else if(node.segments.size()==2&&node.isCall()&&info.member(node.segments.get(1).name)!=null){result.add(node.segments.get(1).name+"()");
}}});return result;}private List<String[]>dialogs(){final List<String[]>result=new ArrayList<String[]>();
final Set<String>seen=new HashSet<String>();for(final Member member:info.members){Node node=member.body!=null?member.body:member.initializer;
if(node==null){continue;}node.visit(new TsSource.Visitor(){public void visit(Node candidate){if(candidate.kind!=Node.Kind.CHAIN){
return;}for(Segment segment:candidate.segments){if("open".equals(segment.name)&&segment.arguments!=null&&!segment.arguments.isEmpty()){
String target=segment.arguments.get(0).path();if(components.containsKey(target)&&seen.add(target+"/"+member.name)){
result.add(new String[]{target,member.method?member.name:null});}}}}});}return result;}private void collectReferences(Element element){
String reference=element.reference();if(reference!=null){references.put(reference,element);}for(Element child:element.children){
collectReferences(child);}}private void walk(Element element,Context context,Out parent){for(Element child:element.children){
if(!child.isText()){visit(child,context,parent);}}}private void visit(Element element,Context context,Out parent){
String tag=element.tag;Context inner=context.loop(element);if(context.nav!=null&&visitNavItem(element,inner,context.nav)){
return;}String navKind=navKind(element);if(navKind!=null&&context.nav==null){Out nav=new Out("nav "+navKind);
parent.children.add(nav);expandMenuModel(element,nav);walk(element,inner.withNav(nav),nav);return;}if(tag.equals("mat-menu")&&context.nav==null){
return;}if(element.isBound("formGroup")){FormModel form=forms.get(expressionName(element.attribute("formGroup")));
if(form==null){form=new FormModel(expressionName(element.attribute("formGroup")),false);forms.put(form.name,form);
}form.placed=true;form.submit=submit(element);Out out=new Out(null);out.form=form;parent.children.add(out);
walk(element,inner.withForm(form,form.root),out);return;}if(tag.equals("form")&&!element.has("ngNoForm")&&context.form==null){
String reference=element.reference();String name=reference!=null?reference:"ngForm";FormModel form=new FormModel(name,true);
form.placed=true;form.submit=submit(element);forms.put("#"+name,form);Out out=new Out(null);out.form=form;
parent.children.add(out);walk(element,inner.withForm(form,form.root),out);return;}if(context.form!=null&&(element.has("formGroupName")||element.has(
"ngModelGroup")||element.has("formArrayName"))){boolean array=element.has("formArrayName");String name=element.has("formGroupName")?element.attribute(
"formGroupName"):array?element.attribute("formArrayName"):element.attribute("ngModelGroup");Control scope=scope(context,name,element.isBound(
"formGroupName"),array?"array":"group");walk(element,inner.withForm(context.form,scope),parent);return;
}String controlName=controlName(element,context);if(controlName!=null){Control control;if(context.form!=null){
control="[]".equals(controlName)?element(context.scope,"control"):context.scope.child(controlName,"control");
}else{control=standaloneControls.get(controlName);if(control==null){control=new Control(controlName,"control");
}standaloneControls.remove(controlName);Out out=new Out(null);out.control=control;parent.children.add(out);
}describe(control,element,inner);return;}if(tag.equals("mat-tab-group")||tag.equals("p-tabView")||tag.equals("p-tabs")||tag.equals("nz-tabset")){
Out tabs=new Out("tabs");parent.children.add(tabs);walk(element,inner,tabs);return;}if(tag.equals("mat-tab")||tag.equals("p-tabPanel")||tag.equals(
"nz-tab")||tag.equals("mat-step")){Out item=new Out(MetadataRenderer.quote(label(element,tag.equals("mat-step")?"matStepLabel":"mat-tab-label")));
parent.children.add(item);walk(element,inner,item);return;}if(tag.matches("mat-(horizontal-|vertical-)?stepper")||tag.equals("p-stepper")){
String linear=element.attribute("linear");Out steps=new Out("steps"+(linear!=null&&!"false".equals(linear)?" linear":""));
parent.children.add(steps);walk(element,inner,steps);return;}if(tag.equals("table")||tag.equals("mat-table")||tag.equals("p-table")){
parent.children.add(new Out(table(element,inner)));return;}if(tag.equals("p-dialog")||tag.equals("dialog")||tag.equals("nz-modal")||tag.equals(
"p-sidebar")){String header=element.attribute("header")!=null?element.attribute("header"):element.attribute("nzTitle");
Out dialog=new Out("dialog"+(header==null?"":" "+MetadataRenderer.quote(header)));parent.children.add(dialog);
walk(element,inner,dialog);return;}Component child=componentsBySelector.get(tag);if(child!=null&&child!=component){
Out usage=new Out("+"+child.info.name);usage.reference=child;parent.children.add(usage);}walk(element,inner,parent);
}private Control scope(Context context,String name,boolean bound,String kind){if(bound&&"array".equals(context.scope.kind)){
return element(context.scope,kind);}Control existing=context.scope.children.get(name);if(existing!=null){
return existing;}return context.scope.child(name,kind);}private Control element(Control array,String kind){
if(array.element==null){array.element=new Control(null,kind);}return array.element;}private String controlName(Element element,Context context){
if(element.has("formControlName")){return element.isBound("formControlName")&&context.scope!=null&&"array".equals(context.scope.kind)?"[]":element.
attribute("formControlName");}if(element.isBound("formControl")){return expressionName(element.attribute("formControl"));
}if(element.has("ngModel")&&!element.tag.equals("form")){String name=element.attribute("name");if(name==null){
String model=element.attribute("ngModel");name=model.isEmpty()?"?":model.substring(model.lastIndexOf('.')+1);
}return name;}return null;}private void describe(Control control,Element element,Context context){if(control.widget==null){
control.widget=widget(element);}String tag=element.tag;String requiredValue=element.attribute("required");
if(requiredValue!=null&&!"false".equals(requiredValue)){control.addValidator("required");}for(String[]attribute:new String[][]{
{"minlength","minLength"},{"maxlength","maxLength"},{"min","min"},{"max","max"}}){String value=element.attribute(attribute[0]);
if(value!=null&&!value.isEmpty()){control.addValidator(attribute[1]+"("+value+")");}}if(element.attribute("pattern")!=null){
control.addValidator("pattern("+MetadataRenderer.quote(element.attribute("pattern"))+")");}if(element.has("email")&&!"false".equals(element.attribute(
"email"))){control.addValidator("email");}if(control.options==null){Element optionsOwner=element;if(element.isBound("matAutocomplete")&&references.
containsKey(element.attribute("matAutocomplete"))){optionsOwner=references.get(element.attribute("matAutocomplete"));
}if(element.attribute("options")!=null){control.options="<"+source(element.attribute("options"));}else if(WIDGETS.containsKey(tag)||tag.equals(
"select")||optionsOwner!=element){control.options=options(optionsOwner,context);}}}private String widget(Element element){
String tag=element.tag;if(tag.equals("input")){if(element.has("matDatepicker")){return"date";}if(element.has("matAutocomplete")){
return"autocomplete";}String type=element.attributes.get("type");return type==null?"text":type.equals("range")?"slider":type;
}if((tag.equals("select")||tag.equals("mat-select")||tag.equals("ng-select"))&&element.has("multiple")&&!"false".equals(element.attribute("multiple"))
){return"multiselect";}if(WIDGETS.containsKey(tag)){return WIDGETS.get(tag);}Component custom=componentsBySelector.get(tag);
return custom!=null?custom.info.name:tag.contains("-")?tag:"?";}private String options(Element owner,Context context){
List<String>values=new ArrayList<String>();String loopSource=optionValues(owner,context,values);if(loopSource!=null){
return"<"+source(loopSource);}if(values.isEmpty()){return null;}List<String>quoted=new ArrayList<String>();
for(String value:values){quoted.add(quoteIfNeeded(value));}return"{"+join(quoted,",")+"}";}private String optionValues(Element element,Context context
,List<String>values){for(Element child:element.children){if(child.isText()){continue;}Context inner=context.loop(child);
if(OPTION_TAGS.contains(child.tag)){if(inner.loopSource!=null&&inner.loopSource!=context.loopSource){
return inner.loopSource;}if(context.loopSource!=null&&child.isBound("value")){return context.loopSource;
}String value=child.attributes.get("value");if(value==null&&child.isBound("value")){String bound=child.attribute("value");
value=bound.matches("'.*'")?bound.substring(1,bound.length()-1):bound;}values.add(value!=null?value:child.text(ICON_TAGS));
}else{String nested=optionValues(child,inner,values);if(nested!=null){return nested;}}}return null;}
private String table(Element element,Context context){String source=element.attribute("dataSource")!=null?element.attribute("dataSource"):element.
attribute("value");List<String>columns=new ArrayList<String>();List<String>headers=new ArrayList<String>();
String[]loop=new String[1];tableParts(element,context,columns,headers,loop);if(source==null){source=loop[0];
}StringBuilder result=new StringBuilder("table");if(source!=null){result.append(' ').append(source(source));
}if(!columns.isEmpty()){result.append(' ').append(join(columns,","));}else if(!headers.isEmpty()){List<String>quoted=new ArrayList<String>();
for(String header:headers){quoted.add(MetadataRenderer.quote(header));}result.append(' ').append(join(quoted,","));
}return result.toString();}private void tableParts(Element element,Context context,List<String>columns,List<String>headers,String[]loop){
for(Element child:element.children){if(child.isText()){continue;}Context inner=context.loop(child);if(child.attribute("matColumnDef")!=null){
columns.add(child.attribute("matColumnDef"));}else if(child.tag.equals("th")&&columns.isEmpty()){headers.add(child.text(ICON_TAGS));
}if(loop[0]==null&&inner.loopSource!=null&&inner.loopSource!=context.loopSource){loop[0]=inner.loopSource;
}tableParts(child,inner,columns,headers,loop);}}private String navKind(Element element){if(NAV_CONTAINERS.containsKey(element.tag)){
return NAV_CONTAINERS.get(element.tag);}if(element.has("mat-tab-nav-bar")){return"tabs";}return null;
}private boolean visitNavItem(Element element,Context context,Out nav){String link=link(element);String action=element.event("click");
String trigger=element.attribute("matMenuTriggerFor");if(link==null&&action==null&&trigger==null){return false;
}if(!(element.tag.equals("a")||element.tag.equals("button")||element.tag.contains("item")||element.tag.equals("li")||element.tag.startsWith("ion-")||
element.has("mat-menu-item")||element.has("mat-list-item"))){return false;}Node items=context.loopVariable==null?null:memberArray(context.
loopExpression);if(items!=null){for(Node item:items.items){if(item.kind==Node.Kind.OBJECT){nav.children.add(new Out(navItem(element,context.
loopVariable,item,link,action)));}}return true;}Out item=new Out(navItem(element,null,null,link,action));
nav.children.add(item);if(trigger!=null&&references.containsKey(trigger)){walk(references.get(trigger),context.withNav(item),item);
}return true;}private String navItem(Element element,String variable,Node item,String link,String action){
String label=element.text(ICON_TAGS);if(item!=null){Matcher matcher=INTERPOLATION.matcher(label);StringBuffer replaced=new StringBuffer();
while(matcher.find()){String value=matcher.group(1).equals(variable)?string(item.properties.get(matcher.group(2))):null;
matcher.appendReplacement(replaced,Matcher.quoteReplacement(value!=null?value:matcher.group()));}matcher.appendTail(replaced);
label=replaced.toString();if(link!=null&&link.startsWith(variable+".")){String value=string(item.properties.get(link.substring(variable.length()+1)));
link=value!=null?value:link;}}if(label.isEmpty()){label=element.attribute("aria-label")!=null?element.attribute("aria-label"):element.text(Collections
.<String>emptySet());}StringBuilder result=new StringBuilder(MetadataRenderer.quote(label));if(link!=null){
result.append(' ').append(quoteIfNeeded(link));}else if(action!=null&&!element.has("matMenuTriggerFor")){
result.append(' ').append(call(action));}return result.toString();}private String link(Element element){
String link=element.attribute("routerLink");if(link==null&&element.attributes.containsKey("href")&&!element.attributes.get("href").startsWith("#")){
link=element.attributes.get("href");}if(link!=null&&element.isBound("routerLink")&&link.startsWith("[")){
Node array=TsSource.parseExpression(link);if(array!=null&&array.kind==Node.Kind.ARRAY){List<String>parts=new ArrayList<String>();
for(Node part:array.items){parts.add(part.kind==Node.Kind.STRING?part.text.replaceAll("^/|/$",""):"{}");
}link=(array.items.isEmpty()||string(array.items.get(0))==null||!string(array.items.get(0)).startsWith("/")?"":"/")+join(parts,"/");
}}else if(link!=null&&element.isBound("routerLink")&&link.matches("'.*'")){link=link.substring(1,link.length()-1);
}return link;}private void expandMenuModel(Element element,Out nav){String model=element.attribute("model");
Node items=model==null?null:memberArray(model);if(items!=null){menuItems(items,nav);}}private void menuItems(Node items,Out parent){
for(Node item:items.items){if(item.kind!=Node.Kind.OBJECT){continue;}String label=string(item.properties.get("label"));
String link=null;for(String key:new String[]{"routerLink","path","link","url","route"}){Node value=item.properties.get(key);
if(value!=null&&link==null){link=string(value)!=null?string(value):value.kind==Node.Kind.ARRAY&&!value.items.isEmpty()?string(value.items.get(0)):null
;}}Out out=new Out(MetadataRenderer.quote(label==null?"":label)+(link==null?"":" "+quoteIfNeeded(link)));
parent.children.add(out);Node children=item.properties.containsKey("items")?item.properties.get("items"):item.properties.get("children");
if(children!=null&&children.kind==Node.Kind.ARRAY){menuItems(children,out);}}}private Node memberArray(String expression){
String name=expressionName(expression);Member member=info.member(name);Node value=member==null?null:resolve(member.initializer);
return value!=null&&value.kind==Node.Kind.ARRAY?value:null;}private String label(Element element,String labelTemplateAttribute){
String label=element.attribute("label");if(label==null){label=element.attribute("header");}if(label==null){
label=element.attribute("nzTitle");}if(label==null){for(Element child:element.children){if(child.has(labelTemplateAttribute)||child.has("*"+
labelTemplateAttribute)){label=child.text(ICON_TAGS);}}}return label==null?"":label;}private String submit(Element form){
String submit=form.event("ngSubmit");if(submit==null){submit=form.event("submit");}if(submit==null){
Element button=find(form,"submit");submit=button==null?null:button.event("click");}return submit==null?null:call(submit);
}private Element find(Element element,String buttonType){for(Element child:element.children){if(child.tag.equals("button")&&buttonType.equals(child.
attribute("type"))){return child;}Element nested=child.isText()?null:find(child,buttonType);if(nested!=null){
return nested;}}return null;}}private static boolean hasSegment(Node chain,String name){for(Segment segment:chain.segments){
if(name.equals(segment.name)){return true;}}return false;}private static String unquote(String key){
return key;}static String call(String expression){Node node=TsSource.parseExpression(expression);if(node!=null&&node.isCall()){
return node.path().replaceFirst("^this\\.","")+"()";}return expression.replace(" ","");}static String source(String expression){
String value=expression.trim();boolean async=value.matches(".*\\|\\s*async.*");value=value.replaceAll("\\|.*","").trim().replaceFirst("^this\\.","").
replace(" ","");return async&&!value.endsWith("$")?value+"|async":value;}static String expressionName(String expression){
return expression.trim().replaceFirst("^this\\.","");}final class Context{FormModel form;Control scope;
Out nav;String loopSource;String loopVariable;String loopExpression;private Context copy(){Context result=new Context();
result.form=form;result.scope=scope;result.nav=nav;result.loopSource=loopSource;result.loopVariable=loopVariable;
result.loopExpression=loopExpression;return result;}Context withForm(FormModel newForm,Control newScope){
Context result=copy();result.form=newForm;result.scope=newScope;return result;}Context withNav(Out newNav){
Context result=copy();result.nav=newNav;return result;}Context loop(Element element){String ngFor=element.attributes.get("*ngFor");
String expression=null;if(ngFor!=null){expression=ngFor.replaceFirst("^\\s*let\\s+","");}else if(element.tag.equals("@for")&&element.attributes.
containsKey("@")){expression=element.attributes.get("@");}if(expression==null){return this;}Matcher matcher=Pattern.compile(
"^\\s*([\\w$]+)\\s+of\\s+([^;]+)").matcher(expression);if(!matcher.find()){return this;}Context result=copy();
result.loopVariable=matcher.group(1);result.loopExpression=matcher.group(2).trim();result.loopSource=result.loopExpression;
return result;}}final static class Template{private static final Set<String>VOID_ELEMENTS=new HashSet<String>(Arrays.asList("area","base","br","col",
"embed","hr","img","input","link","meta","param","source","track","wbr"));static final class Element{
final String tag;final Map<String,String>attributes=new LinkedHashMap<String,String>();final List<Element>children=new ArrayList<Element>();
Element parent;String text;Element(String tag){this.tag=tag;}boolean isText(){return"#text".equals(tag);
}String attribute(String name){if(attributes.containsKey(name)){return attributes.get(name);}if(attributes.containsKey("["+name+"]")){
return attributes.get("["+name+"]");}return attributes.get("[("+name+")]");}boolean has(String name){
return attribute(name)!=null;}boolean isBound(String name){return attributes.containsKey("["+name+"]")||attributes.containsKey("[("+name+")]");
}String event(String name){String value=attributes.get("("+name+")");return value!=null?value:attributes.get("on-"+name);
}String reference(){for(String attribute:attributes.keySet()){if(attribute.startsWith("#")){return attribute.substring(1);
}if(attribute.startsWith("ref-")){return attribute.substring(4);}}return null;}String text(Set<String>excludedTags){
StringBuilder result=new StringBuilder();appendText(this,excludedTags,result);return result.toString().replaceAll("\\s+"," ").replaceAll("\\{\\{\\s*",
"{{").replaceAll("\\s*}}","}}").trim();}private static void appendText(Element element,Set<String>excludedTags,StringBuilder result){
if(element.isText()){result.append(element.text).append(' ');return;}if(excludedTags.contains(element.tag)||element.has("matListItemIcon")||element.
tag.startsWith("@")&&!"@if".equals(element.tag)&&!"@for".equals(element.tag)){return;}for(Element child:element.children){
appendText(child,excludedTags,result);}}}private final String text;private int position;private Template(String text){
this.text=text;}static Element parse(String template){Template parser=new Template(template);Element root=new Element("#root");
parser.parseChildren(root,null);return root;}private void parseChildren(Element parent,String closingTag){
boolean block=parent.tag.startsWith("@");StringBuilder textContent=new StringBuilder();while(position<text.length()){
if(text.startsWith("<!--",position)){int end=text.indexOf("-->",position);position=end<0?text.length():end+3;
}else if(text.startsWith("</",position)){flushText(parent,textContent);int end=text.indexOf('>',position);
String tag=text.substring(position+2,end<0?text.length():end).trim();if(tag.equals(closingTag)||!hasOpenAncestor(parent,tag)){
position=end<0?text.length():end+1;if(tag.equals(closingTag)){return;}continue;}return;}else if(text.charAt(position)=='<'&&position+1<text.length()&&
(Character.isLetter(text.charAt(position+1)))){flushText(parent,textContent);parseElement(parent);}else if(text.startsWith("{{",position)){
int end=text.indexOf("}}",position);end=end<0?text.length():end+2;textContent.append(text,position,end);
position=end;}else if(text.charAt(position)=='@'&&position+1<text.length()&&Character.isLetter(text.charAt(position+1))){
flushText(parent,textContent);parseBlock(parent);}else if(text.charAt(position)=='}'&&block){flushText(parent,textContent);
position++;return;}else{textContent.append(text.charAt(position++));}}flushText(parent,textContent);
}private static boolean hasOpenAncestor(Element element,String tag){for(Element current=element;current!=null;
current=current.parent){if(current.tag.equals(tag)){return true;}}return false;}private static void flushText(Element parent,StringBuilder textContent
){if(textContent.toString().trim().length()>0){Element textElement=new Element("#text");textElement.text=textContent.toString();
textElement.parent=parent;parent.children.add(textElement);}textContent.setLength(0);}private void parseElement(Element parent){
position++;int start=position;while(position<text.length()&&!Character.isWhitespace(text.charAt(position))&&text.charAt(position)!='>'&&text.charAt(
position)!='/'){position++;}Element element=new Element(text.substring(start,position));element.parent=parent;
parent.children.add(element);boolean selfClosing=false;while(position<text.length()){skipWhitespace();
if(text.startsWith("/>",position)){position+=2;selfClosing=true;break;}if(position>=text.length()||text.charAt(position)=='>'){
position++;break;}int nameStart=position;while(position<text.length()&&!Character.isWhitespace(text.charAt(position))&&text.charAt(position)!='='&&
text.charAt(position)!='>'&&!text.startsWith("/>",position)){position++;}String name=text.substring(nameStart,position);
if(name.isEmpty()){position++;continue;}skipWhitespace();String value="";if(position<text.length()&&text.charAt(position)=='='){
position++;skipWhitespace();char quote=position<text.length()?text.charAt(position):' ';if(quote=='"'||quote=='\''){
int end=text.indexOf(quote,position+1);end=end<0?text.length():end;value=text.substring(position+1,end);
position=end+1;}else{int valueStart=position;while(position<text.length()&&!Character.isWhitespace(text.charAt(position))&&text.charAt(position)!='>')
{position++;}value=text.substring(valueStart,position);}}element.attributes.put(name,value.trim());}
if(!selfClosing&&!VOID_ELEMENTS.contains(element.tag.toLowerCase())){if("script".equals(element.tag)||"style".equals(element.tag)){
int end=text.indexOf("</"+element.tag,position);position=end<0?text.length():text.indexOf('>',end)+1;
}else{parseChildren(element,element.tag);}}}private void parseBlock(Element parent){position++;int start=position;
while(position<text.length()&&(Character.isLetter(text.charAt(position))||text.charAt(position)==' '&&text.startsWith(" if",position)&&text.substring(
start,position).equals("else"))){position++;}String name=text.substring(start,position).trim();if("let".equals(name)){
int end=text.indexOf(';',position);position=end<0?text.length():end+1;return;}Element block=new Element("@"+name.replace(' ','-'));
block.parent=parent;parent.children.add(block);skipWhitespace();if(position<text.length()&&text.charAt(position)=='('){
int depth=0;int parameterStart=position+1;do{char c=text.charAt(position);if(c=='('){depth++;}else if(c==')'){
depth--;}else if(c=='\''||c=='"'){int end=text.indexOf(c,position+1);position=end<0?text.length()-1:end;
}position++;}while(depth>0&&position<text.length());block.attributes.put("@",text.substring(parameterStart,position-1).trim());
}skipWhitespace();if(position<text.length()&&text.charAt(position)=='{'){position++;parseChildren(block,null);
}}private void skipWhitespace(){while(position<text.length()&&Character.isWhitespace(text.charAt(position))){
position++;}}}final static class TsSource{enum T{IDENT,NUMBER,STRING,TEMPLATE,REGEX,PUNCT,EOF}static final class Token{
final T type;final String text;final boolean newlineBefore;Token(T type,String text,boolean newlineBefore){
this.type=type;this.text=text;this.newlineBefore=newlineBefore;}boolean is(String value){return(type==T.PUNCT||type==T.IDENT)&&text.equals(value);
}public String toString(){return text;}}static final class Node{enum Kind{STRING,NUMBER,REGEX,TEMPLATE,KEYWORD,CHAIN,NEW,OBJECT,ARRAY,ARROW,ASSIGN,
BLOCK,OTHER}final Kind kind;String text;String typeArguments;Node base;final List<Segment>segments=new ArrayList<Segment>();
final List<Node>items=new ArrayList<Node>();final Map<String,Node>properties=new LinkedHashMap<String,Node>();
Node target;Node value;List<String>parameters=new ArrayList<String>();Node(Kind kind,String text){this.kind=kind;
this.text=text;}boolean isCall(){return kind==Kind.CHAIN&&!segments.isEmpty()&&segments.get(segments.size()-1).arguments!=null;
}Segment last(){return segments.isEmpty()?null:segments.get(segments.size()-1);}String path(){StringBuilder result=new StringBuilder();
for(Segment segment:segments){if(segment.name!=null){result.append(result.length()>0?".":"").append(segment.name);
}}return result.toString();}void visit(Visitor visitor){visitor.visit(this);if(base!=null){base.visit(visitor);
}for(Segment segment:segments){if(segment.arguments!=null){for(Node argument:segment.arguments){argument.visit(visitor);
}}if(segment.index!=null){segment.index.visit(visitor);}}for(Node item:items){item.visit(visitor);}for(Node property:properties.values()){
property.visit(visitor);}if(target!=null){target.visit(visitor);}if(value!=null){value.visit(visitor);
}}String stringValue(){return kind==Kind.STRING||kind==Kind.TEMPLATE||kind==Kind.NUMBER||kind==Kind.KEYWORD?text:null;
}}interface Visitor{void visit(Node node);}static final class Segment{final String name;List<Node>arguments;
String typeArguments;Node index;Segment(String name){this.name=name;}}static final class Decorator{final String name;
final List<Node>arguments;Decorator(String name,List<Node>arguments){this.name=name;this.arguments=arguments;
}Node argument(){return arguments.isEmpty()?null:arguments.get(0);}}static final class Param{String name;
String type;final List<Decorator>decorators=new ArrayList<Decorator>();}static final class Member{String name;
boolean method;boolean getter;boolean isStatic;boolean isPrivate;String type;Node initializer;Node body;
final List<Param>parameters=new ArrayList<Param>();final List<Decorator>decorators=new ArrayList<Decorator>();
}static final class ClassInfo{String name;TsSource source;String superClass;final List<String>interfaces=new ArrayList<String>();
final List<Decorator>decorators=new ArrayList<Decorator>();final List<Member>members=new ArrayList<Member>();
final List<Param>constructorParameters=new ArrayList<Param>();Node constructorBody;Decorator decorator(String decoratorName){
for(Decorator decorator:decorators){if(decorator.name.equals(decoratorName)){return decorator;}}return null;
}Member member(String memberName){for(Member member:members){if(member.name.equals(memberName)){return member;
}}return null;}}private static final Set<String>BINARY=new HashSet<String>(Arrays.asList("+","-","*","/","%","**","==","===","!=","!==","<",">","<=",
">=","&&","||","??","&","|","^","<<",">>",">>>","instanceof","in"));private static final Set<String>ASSIGNMENTS=new HashSet<String>(Arrays.asList("=",
"+=","-=","*=","/=","%=","&&=","||=","??=","|=","&="));private static final Set<String>REGEX_PREFIX=new HashSet<String>(Arrays.asList("(",",","=",":",
"[","!","&","|","?","{","}",";","=>","return","&&","||","??","+","-","*","%","==","===","!=","!==","typeof","case"));
private static final String[]PUNCTUATION={">>>=","...","===","!==","**=","<<=",">>=","&&=","||=","??=","=>","?.","??","==","!=","<=",">=","&&","||",
"++","--","+=","-=","*=","/=","%=","&=","|=","^=","**","<<"};final String path;final List<ClassInfo>classes=new ArrayList<ClassInfo>();
final Map<String,Node>variables=new LinkedHashMap<String,Node>();final Map<String,String>variableTypes=new LinkedHashMap<String,String>();
final List<Node>statements=new ArrayList<Node>();private final List<Token>tokens;private int position;
private TsSource(String path,String text){this.path=path;this.tokens=tokenize(text);}static TsSource parse(String path,String text){
TsSource source=new TsSource(path,text);source.parseTopLevel();return source;}static Node parseExpression(String expression){
TsSource source=new TsSource("",expression);return source.tokens.size()<=1?null:source.expression();
}private static List<Token>tokenize(String text){List<Token>result=new ArrayList<Token>();int i=0;boolean newline=false;
while(i<text.length()){char c=text.charAt(i);if(c=='\n'){newline=true;i++;}else if(Character.isWhitespace(c)){
i++;}else if(text.startsWith("//",i)){while(i<text.length()&&text.charAt(i)!='\n'){i++;}}else if(text.startsWith("/*",i)){
int end=text.indexOf("*/",i+2);i=end<0?text.length():end+2;newline=true;}else if(Character.isJavaIdentifierStart(c)||c=='#'){
int start=i++;while(i<text.length()&&Character.isJavaIdentifierPart(text.charAt(i))){i++;}result.add(new Token(T.IDENT,text.substring(start,i),newline
));newline=false;}else if(Character.isDigit(c)||(c=='.'&&i+1<text.length()&&Character.isDigit(text.charAt(i+1)))){
int start=i++;while(i<text.length()&&(Character.isLetterOrDigit(text.charAt(i))||text.charAt(i)=='.'||text.charAt(i)=='_')){
i++;}result.add(new Token(T.NUMBER,text.substring(start,i).replace("_",""),newline));newline=false;}
else if(c=='"'||c=='\''){StringBuilder value=new StringBuilder();i++;while(i<text.length()&&text.charAt(i)!=c){
if(text.charAt(i)=='\\'&&i+1<text.length()){i++;char escaped=text.charAt(i);value.append(escaped=='n'?'\n':escaped=='t'?'\t':escaped);
}else{value.append(text.charAt(i));}i++;}i++;result.add(new Token(T.STRING,value.toString(),newline));
newline=false;}else if(c=='`'){int start=++i;int depth=0;while(i<text.length()&&(text.charAt(i)!='`'||depth>0)){
if(text.charAt(i)=='\\'){i++;}else if(text.startsWith("${",i)){depth++;i++;}else if(text.charAt(i)=='}'&&depth>0){
depth--;}i++;}result.add(new Token(T.TEMPLATE,text.substring(start,Math.min(i,text.length())),newline));
i++;newline=false;}else if(c=='/'&&isRegexStart(result)){int start=i++;boolean inClass=false;while(i<text.length()&&(text.charAt(i)!='/'||inClass)&&
text.charAt(i)!='\n'){if(text.charAt(i)=='\\'){i++;}else if(text.charAt(i)=='['){inClass=true;}else if(text.charAt(i)==']'){
inClass=false;}i++;}i++;while(i<text.length()&&Character.isLetter(text.charAt(i))){i++;}result.add(new Token(T.REGEX,text.substring(start,Math.min(i,
text.length())),newline));newline=false;}else{String punctuation=String.valueOf(c);for(String candidate:PUNCTUATION){
if(text.startsWith(candidate,i)){punctuation=candidate;break;}}if("?.".equals(punctuation)&&i+2<text.length()&&Character.isDigit(text.charAt(i+2))){
punctuation="?";}result.add(new Token(T.PUNCT,punctuation,newline));i+=punctuation.length();newline=false;
}}result.add(new Token(T.EOF,"",true));return result;}private static boolean isRegexStart(List<Token>previous){
if(previous.isEmpty()){return true;}Token last=previous.get(previous.size()-1);return(last.type==T.PUNCT||last.type==T.IDENT)&&REGEX_PREFIX.contains(
last.text);}private void parseTopLevel(){List<Decorator>decorators=new ArrayList<Decorator>();while(peek().type!=T.EOF){
int start=position;Token token=peek();if(token.is("@")){decorators.add(decorator());}else if(token.is("import")&&!peekAt(1).is("(")){
skipStatement();}else if(token.is("export")||token.is("default")||token.is("declare")||token.is("abstract")){
position++;}else if(token.is("class")){ClassInfo info=classDeclaration();info.decorators.addAll(decorators);
decorators.clear();classes.add(info);}else if((token.is("const")||token.is("let")||token.is("var"))&&peekAt(1).type==T.IDENT){
position++;String name=next().text;String type=null;if(accept(":")){type=skipType();}if(accept("=")){
variables.put(name,expression());variableTypes.put(name,type);}accept(";");}else if(token.is("function")){
Node function=primary();if(function.text!=null){variables.put(function.text,function);}}else if(token.is("interface")||token.is("type")||token.is(
"enum")||token.is("namespace")||token.is("module")){skipDeclaration();}else{Node statement=statement();
if(statement!=null){statements.add(statement);}}if(position==start){position++;}}}private void skipStatement(){
while(peek().type!=T.EOF&&!accept(";")){if(position>0&&peek().newlineBefore&&peek().is("import")){return;
}if(peek().is("{")){skipBalanced();}else{position++;}}}private void skipDeclaration(){while(peek().type!=T.EOF){
if(peek().is("{")){skipBalanced();return;}if(accept(";")){return;}if(peek().is("=")){position++;skipType();
accept(";");return;}position++;}}private Decorator decorator(){expect("@");StringBuilder name=new StringBuilder(next().text);
while(accept(".")){name.append('.').append(next().text);}List<Node>arguments=new ArrayList<Node>();if(peek().is("(")){
arguments=arguments();}return new Decorator(name.toString(),arguments);}private ClassInfo classDeclaration(){
expect("class");ClassInfo info=new ClassInfo();info.source=this;info.name=peek().type==T.IDENT&&!peek().is("extends")&&!peek().is("implements")?next()
.text:"";if(peek().is("<")){skipAngle();}while(!peek().is("{")&&peek().type!=T.EOF){if(accept("extends")){
info.superClass=next().text;if(peek().is("<")){skipAngle();}}else if(accept("implements")){do{info.interfaces.add(next().text);
if(peek().is("<")){skipAngle();}}while(accept(","));}else{position++;}}expect("{");List<Decorator>decorators=new ArrayList<Decorator>();
while(!peek().is("}")&&peek().type!=T.EOF){int start=position;if(peek().is("@")){decorators.add(decorator());
continue;}if(accept(";")){continue;}Member member=new Member();member.decorators.addAll(decorators);
decorators.clear();while(isModifier(peek(),peekAt(1))){String modifier=next().text;member.isStatic|="static".equals(modifier);
member.isPrivate|="private".equals(modifier)||"protected".equals(modifier);member.getter|="get".equals(modifier);
}if(peek().is("constructor")&&peekAt(1).is("(")){position++;info.constructorParameters.addAll(parameters());
info.constructorBody=peek().is("{")?block():null;continue;}Token name=next();if(name.is("[")){position--;
skipBalanced();skipMemberRest();continue;}if(name.type==T.PUNCT&&name.text.startsWith("#")||name.is("*")){
continue;}member.name=name.text;accept("?");accept("!");if(peek().is("(")||peek().is("<")){member.method=true;
if(peek().is("<")){skipAngle();}member.parameters.addAll(parameters());if(accept(":")){member.type=skipReturnType();
}member.body=peek().is("{")?block():null;}else{if(accept(":")){member.type=skipType();}if(accept("=")){
member.initializer=expression();}}accept(";");info.members.add(member);if(position==start){position++;
}}expect("}");return info;}private static boolean isModifier(Token token,Token following){if(token.type!=T.IDENT){
return false;}boolean keyword=Arrays.asList("public","private","protected","readonly","static","abstract","override","declare","async","accessor",
"get","set").contains(token.text);return keyword&&(following.type==T.IDENT||following.type==T.STRING||following.is("[")||following.is("#")||following.
is("*"));}private void skipMemberRest(){while(peek().type!=T.EOF&&!peek().is(";")&&!peek().is("}")&&!peek().newlineBefore){
if(peek().is("{")||peek().is("(")||peek().is("[")){skipBalanced();}else{position++;}}accept(";");}private List<Param>parameters(){
List<Param>result=new ArrayList<Param>();expect("(");while(!peek().is(")")&&peek().type!=T.EOF){Param parameter=new Param();
while(peek().is("@")){parameter.decorators.add(decorator());}while(isModifier(peek(),peekAt(1))){position++;
}accept("...");if(peek().is("{")||peek().is("[")){skipBalanced();parameter.name="_";}else{parameter.name=next().text;
}accept("?");if(accept(":")){parameter.type=skipTypeUntil(",",")","=");}if(accept("=")){expression();
}result.add(parameter);if(!accept(",")){break;}}expect(")");return result;}private String skipType(){
return skipTypeUntil(";","=",")",",","}");}private String skipTypeUntil(String...ends){StringBuilder result=new StringBuilder();
int depth=0;int start=position;while(peek().type!=T.EOF){Token token=peek();if(depth==0&&position>start&&token.newlineBefore&&!isTypeContinuation(
token)&&!isTypeContinuation(tokens.get(position-1))){break;}if(depth==0&&Arrays.asList(ends).contains(token.text)&&token.type==T.PUNCT){
break;}if(token.is("(")||token.is("[")||token.is("{")||token.is("<")){depth++;}else if(token.is(")")||token.is("]")||token.is("}")||token.is(">")){
if(depth==0){break;}depth--;}else if(token.is(">>")){depth-=2;}result.append(token.type==T.STRING?"'"+token.text+"'":token.text);
position++;}return result.toString();}private static boolean isTypeContinuation(Token token){return token.is("|")||token.is("&")||token.is("=>")||
token.is(",")||token.is(":")||token.is(".");}private String skipReturnType(){StringBuilder result=new StringBuilder();
int depth=0;while(peek().type!=T.EOF){Token token=peek();if(depth==0&&(token.is("{")&&result.length()>0||token.is(";"))){
break;}if(token.is("(")||token.is("[")||token.is("<")||token.is("{")){depth++;}else if(token.is(")")||token.is("]")||token.is(">")||token.is("}")){
depth--;}else if(token.is(">>")){depth-=2;}result.append(token.text);position++;}return result.toString();
}private String skipAngle(){StringBuilder result=new StringBuilder();int depth=0;do{Token token=next();
if(token.is("<")){depth++;}else if(token.is(">")){depth--;}else if(token.is(">>")){depth-=2;}else if(token.is(">>>")){
depth-=3;}result.append(token.type==T.STRING?"'"+token.text+"'":token.text);}while(depth>0&&peek().type!=T.EOF);
return result.toString();}private boolean isTypeArgumentList(){int depth=0;for(int i=position;i<tokens.size();
i++){Token token=tokens.get(i);if(token.is("<")){depth++;}else if(token.is(">")||token.is(">>")){depth-=token.text.length();
if(depth<=0){return depth==0&&i+1<tokens.size()&&tokens.get(i+1).is("(");}}else if(token.is(";")||token.is("{")||token.is("}")||token.is("=>")||token.
is("&&")||token.is("||")||token.type==T.EOF){return false;}}return false;}Node block(){Node result=new Node(Node.Kind.BLOCK,null);
expect("{");while(!peek().is("}")&&peek().type!=T.EOF){int start=position;Node statement=statement();
if(statement!=null){result.items.add(statement);}if(position==start){position++;}}accept("}");return result;
}private Node statement(){Token token=peek();if(accept(";")){return null;}if(token.is("{")){return block();
}if(token.is("const")||token.is("let")||token.is("var")){position++;Node result=new Node(Node.Kind.BLOCK,null);
do{if(peek().is("{")||peek().is("[")){skipBalanced();if(accept("=")){result.items.add(expression());
}continue;}Node target=new Node(Node.Kind.CHAIN,null);target.segments.add(new Segment(next().text));
if(accept(":")){skipType();}if(accept("=")){Node assignment=new Node(Node.Kind.ASSIGN,"=");assignment.target=target;
assignment.value=expression();result.items.add(assignment);}}while(accept(","));return result;}if(token.is("if")||token.is("while")||token.is("for")||
token.is("switch")||token.is("catch")){position++;Node result=new Node(Node.Kind.BLOCK,null);if(peek().is("await")){
position++;}if(accept("(")){while(!peek().is(")")&&peek().type!=T.EOF){int start=position;Node inner=statement();
if(inner!=null){result.items.add(inner);}if(position==start){position++;}}accept(")");}Node body=statement();
if(body!=null){result.items.add(body);}return result;}if(token.is("else")||token.is("do")||token.is("try")||token.is("finally")){
position++;return statement();}if(token.is("return")||token.is("throw")||token.is("yield")||token.is("await")){
position++;if(peek().is(";")||peek().is("}")||peek().newlineBefore){return null;}return expression();
}if(token.is("case")){position++;expression();accept(":");return null;}if(token.is("default")&&peekAt(1).is(":")||token.is("break")||token.is(
"continue")){position++;accept(":");return null;}if(token.is("in")||token.is("of")){position++;return null;
}return expression();}Node expression(){Node left=binary();if(ASSIGNMENTS.contains(peek().text)&&peek().type==T.PUNCT){
Node result=new Node(Node.Kind.ASSIGN,next().text);result.target=left;result.value=expression();return result;
}return left;}private Node binary(){Node first=unary();Node result=null;while(true){Token token=peek();
if(token.is("as")||token.is("satisfies")){position++;skipType();continue;}if(token.is("?")&&!peekAt(1).is(":")){
position++;Node other=result==null?new Node(Node.Kind.OTHER,"?:"):result;if(result==null){other.items.add(first);
}other.items.add(expression());accept(":");other.items.add(expression());result=other;continue;}if((token.type==T.PUNCT||token.type==T.IDENT)&&BINARY.
contains(token.text)&&!(token.type==T.IDENT&&token.newlineBefore)){position++;if(result==null){result=new Node(Node.Kind.OTHER,token.text);
result.items.add(first);}result.items.add(unary());continue;}return result==null?first:result;}}private Node unary(){
Token token=peek();if(token.is("!")||token.is("-")||token.is("+")||token.is("~")||token.is("typeof")||token.is("void")||token.is("delete")||token.is(
"await")||token.is("...")||token.is("++")||token.is("--")){position++;return unary();}if(token.is("<")&&peekAt(1).type==T.IDENT){
skipAngle();return unary();}if(token.is("new")){position++;Node result=new Node(Node.Kind.NEW,null);
StringBuilder name=new StringBuilder(peek().type==T.IDENT?next().text:"");while(accept(".")){name.append('.').append(next().text);
}result.text=name.toString();if(peek().is("<")){result.typeArguments=skipAngle();}if(peek().is("(")){
result.items.addAll(arguments());}return postfix(result);}return postfix(primary());}private Node postfix(Node primary){
Node chain=primary;while(true){Token token=peek();if(token.is(".")||token.is("?.")){position++;if(peek().is("(")){
continue;}if(peek().is("[")){continue;}chain=asChain(chain);chain.segments.add(new Segment(next().text));
}else if(token.is("(")&&!token.newlineBefore){chain=asChain(chain);Segment last=chain.last();if(last==null||last.arguments!=null||last.index!=null){
last=new Segment(null);chain.segments.add(last);}last.arguments=arguments();}else if(token.is("<")&&chain.kind==Node.Kind.CHAIN&&isTypeArgumentList())
{String typeArguments=skipAngle();chain.last().typeArguments=typeArguments;}else if(token.is("[")&&!token.newlineBefore){
chain=asChain(chain);position++;Segment segment=new Segment(null);segment.index=peek().is("]")?null:expression();
accept("]");chain.segments.add(segment);}else if(token.is("!")&&!peekAt(1).is("=")){position++;}else if(token.type==T.TEMPLATE&&!token.newlineBefore){
position++;}else if(token.is("++")||token.is("--")){position++;}else{return chain;}}}private static Node asChain(Node node){
if(node.kind==Node.Kind.CHAIN){return node;}Node chain=new Node(Node.Kind.CHAIN,null);chain.base=node;
return chain;}private Node primary(){if(peek().is(")")||peek().is("]")||peek().is("}")||peek().is(";")||peek().is(",")){
return new Node(Node.Kind.OTHER,"");}Token token=next();switch(token.type){case STRING:return new Node(Node.Kind.STRING,token.text);
case NUMBER:return new Node(Node.Kind.NUMBER,token.text);case TEMPLATE:return new Node(Node.Kind.TEMPLATE,token.text);
case REGEX:return new Node(Node.Kind.REGEX,token.text);case EOF:position--;return new Node(Node.Kind.OTHER,"");
default:break;}if(token.type==T.IDENT){if(Arrays.asList("true","false","null","undefined").contains(token.text)){
return new Node(Node.Kind.KEYWORD,token.text);}if(token.is("async")&&(peek().is("(")||peek().type==T.IDENT&&peekAt(1).is("=>")||peek().is("function"))
){return primary();}if(token.is("function")){Node function=new Node(Node.Kind.ARROW,peek().type==T.IDENT?next().text:null);
accept("*");for(Param parameter:parameters()){function.parameters.add(parameter.name);}if(accept(":")){
skipReturnType();}function.items.add(peek().is("{")?block():new Node(Node.Kind.OTHER,""));return function;
}if(peek().is("=>")){position++;Node arrow=new Node(Node.Kind.ARROW,null);arrow.parameters.add(token.text);
arrow.items.add(peek().is("{")?block():expression());return arrow;}Node chain=new Node(Node.Kind.CHAIN,null);
chain.segments.add(new Segment(token.text));return chain;}if(token.is("(")){position--;if(isArrowAhead()){
Node arrow=new Node(Node.Kind.ARROW,null);for(Param parameter:parameters()){arrow.parameters.add(parameter.name);
}if(accept(":")){skipTypeUntil("=>");}expect("=>");arrow.items.add(peek().is("{")?block():expression());
return arrow;}position++;Node inner=peek().is(")")?new Node(Node.Kind.OTHER,""):expression();while(accept(",")){
expression();}accept(")");return inner;}if(token.is("[")){Node array=new Node(Node.Kind.ARRAY,null);
while(!peek().is("]")&&peek().type!=T.EOF){if(accept(",")){continue;}int start=position;array.items.add(expression());
if(position==start){position++;}accept(",");}accept("]");return array;}if(token.is("{")){Node object=new Node(Node.Kind.OBJECT,null);
int spread=0;while(!peek().is("}")&&peek().type!=T.EOF){int start=position;if(accept(",")){continue;
}if(accept("...")){object.properties.put("..."+spread++,expression());continue;}while(isModifier(peek(),peekAt(1))){
position++;}String key;if(peek().is("[")){position++;key="["+expression().path()+"]";accept("]");}else{
key=next().text;}if(accept(":")){object.properties.put(key,expression());}else if(peek().is("(")){Node method=new Node(Node.Kind.ARROW,key);
for(Param parameter:parameters()){method.parameters.add(parameter.name);}if(accept(":")){skipReturnType();
}method.items.add(peek().is("{")?block():new Node(Node.Kind.OTHER,""));object.properties.put(key,method);
}else{Node shorthand=new Node(Node.Kind.CHAIN,null);shorthand.segments.add(new Segment(key));if(accept("=")){
expression();}object.properties.put(key,shorthand);}if(position==start){position++;}}accept("}");return object;
}return new Node(Node.Kind.OTHER,token.text);}private boolean isArrowAhead(){int depth=0;for(int i=position;
i<tokens.size();i++){Token token=tokens.get(i);if(token.is("(")){depth++;}else if(token.is(")")){depth--;
if(depth==0){Token following=i+1<tokens.size()?tokens.get(i+1):null;return following!=null&&(following.is("=>")||following.is(":")&&
arrowAfterReturnType(i+2));}}else if(token.type==T.EOF){return false;}}return false;}private boolean arrowAfterReturnType(int from){
int depth=0;for(int i=from;i<tokens.size()&&i<from+64;i++){Token token=tokens.get(i);if(depth==0&&token.is("=>")){
return true;}if(token.is("(")||token.is("<")||token.is("[")||token.is("{")){depth++;}else if(token.is(")")||token.is(">")||token.is("]")||token.is("}"
)){depth--;if(depth<0){return false;}}else if(depth==0&&(token.is(";")||token.is(",")||token.is("?"))){
return false;}}return false;}private List<Node>arguments(){List<Node>result=new ArrayList<Node>();expect("(");
while(!peek().is(")")&&peek().type!=T.EOF){int start=position;result.add(expression());if(position==start){
position++;}accept(",");}accept(")");return result;}private void skipBalanced(){int depth=0;do{Token token=next();
if(token.is("{")||token.is("(")||token.is("[")){depth++;}else if(token.is("}")||token.is(")")||token.is("]")){
depth--;}}while(depth>0&&peek().type!=T.EOF);}private Token peek(){return tokens.get(Math.min(position,tokens.size()-1));
}private Token peekAt(int offset){return tokens.get(Math.min(position+offset,tokens.size()-1));}private Token next(){
Token token=peek();if(position<tokens.size()-1){position++;}return token;}private boolean accept(String text){
if(peek().is(text)){position++;return true;}return false;}private void expect(String text){accept(text);
}}}

// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.maven;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import java.util.*;import java.util.regex.*;import javax.xml.XMLConstants;import javax.xml.parsers.*;
import org.os890.cdi.callrecorder.core.CallRecorder.ShortNames;import org.w3c.dom.*;import org.xml.sax.SAXException;
public final class MavenModuleAnalyzer{private static final Pattern PROPERTY=Pattern.compile("\\$\\{([^}]+)}");
private final File entryPom;private final List<String>profiles=new ArrayList<String>();private final Map<String,Module>modulesByKey=new HashMap<String
,Module>();private final ShortNames names=new ShortNames();private MavenModuleAnalyzer(File entryPom){
this.entryPom=entryPom.isDirectory()?new File(entryPom,"pom.xml"):entryPom;}public static String analyze(File pom,String...profiles){
return of(pom).profiles(profiles).analyze();}public static MavenModuleAnalyzer of(File pom){return new MavenModuleAnalyzer(pom);
}public MavenModuleAnalyzer profiles(String...ids){for(String id:ids){for(String part:id.split(",")){
if(!part.trim().isEmpty()){profiles.add(part.trim());}}}return this;}public static void main(String[]args){
if(args.length==0){System.err.println("usage: "+MavenModuleAnalyzer.class.getName()+" <pom.xml> [<profile>...]");
return;}System.out.print(analyze(new File(args[0]),Arrays.copyOfRange(args,1,args.length)));}public synchronized String analyze(){
modulesByKey.clear();Module root=read(entryPom.getAbsoluteFile(),null,null);StringBuilder result=new StringBuilder();
write(root,0,result);return result.toString();}private Module read(File pom,Module aggregator,String path){
Element project=parse(pom);Module module=new Module(aggregator,path);Element parent=child(project,"parent");
Map<String,String>properties=new HashMap<String,String>();if(parent!=null){module.parentKey=text(parent,"groupId")+":"+text(parent,"artifactId");
Module parentModule=modulesByKey.get(module.parentKey);if(parentModule!=null){properties.putAll(parentModule.properties);
}properties.put("project.parent.groupId",text(parent,"groupId"));properties.put("project.parent.version",text(parent,"version"));
}module.groupId=text(project,"groupId")!=null?text(project,"groupId"):parent==null?"":text(parent,"groupId");
module.artifactId=text(project,"artifactId");module.packaging=text(project,"packaging")==null?"jar":text(project,"packaging");
String version=text(project,"version")!=null?text(project,"version"):parent==null?null:text(parent,"version");
properties.put("project.groupId",module.groupId);properties.put("groupId",module.groupId);properties.put("pom.groupId",module.groupId);
properties.put("project.artifactId",module.artifactId);properties.put("project.version",version);properties.put("version",version);
List<Element>sections=new ArrayList<Element>();sections.add(project);sections.addAll(activeProfiles(project));
for(Element section:sections){Element sectionProperties=child(section,"properties");if(sectionProperties!=null){
for(Element property:children(sectionProperties,null)){properties.put(property.getTagName(),property.getTextContent().trim());
}}}module.properties=properties;module.groupId=interpolate(module.groupId,properties);module.artifactId=interpolate(module.artifactId,properties);
module.packaging=interpolate(module.packaging,properties);modulesByKey.put(module.key(),module);for(Element section:sections){
Element dependencies=child(section,"dependencies");if(dependencies!=null){for(Element dependency:children(dependencies,"dependency")){
module.dependencies.add(new Dependency(interpolate(text(dependency,"groupId"),properties)+":"+interpolate(text(dependency,"artifactId"),properties),
interpolate(text(dependency,"scope"),properties),interpolate(text(dependency,"type"),properties)));}
}}for(Element section:sections){Element modules=child(section,"modules");if(modules!=null){for(Element child:children(modules,"module")){
String childPath=child.getTextContent().trim();File childPom=new File(pom.getParentFile(),childPath);
if(childPom.isDirectory()){childPom=new File(childPom,"pom.xml");}module.modules.add(read(normalize(childPom),module,childPath));
}}}return module;}private List<Element>activeProfiles(Element project){List<Element>result=new ArrayList<Element>();
Element profilesElement=child(project,"profiles");if(profilesElement==null){return result;}List<Element>all=children(profilesElement,"profile");
boolean explicit=false;for(Element profile:all){explicit|=profiles.contains(text(profile,"id"));}for(Element profile:all){
String id=text(profile,"id");Element activation=child(profile,"activation");boolean byDefault=activation!=null&&"true".equals(text(activation,
"activeByDefault"));boolean active=profiles.contains(id)||(byDefault&&!explicit);if(active&&!profiles.contains("!"+id)&&!profiles.contains("-"+id)){
result.add(profile);}}return result;}private static String interpolate(String value,Map<String,String>properties){
if(value==null){return null;}String result=value;for(int i=0;i<10&&result.contains("${");i++){Matcher matcher=PROPERTY.matcher(result);
StringBuffer replaced=new StringBuffer();while(matcher.find()){String property=properties.get(matcher.group(1));
matcher.appendReplacement(replaced,Matcher.quoteReplacement(property!=null?property:matcher.group()));
}matcher.appendTail(replaced);if(replaced.toString().equals(result)){break;}result=replaced.toString();
}return result;}private void write(Module module,int depth,StringBuilder result){for(int i=0;i<depth;
i++){result.append(' ');}result.append(name(module));if(module.path!=null&&!module.path.equals(module.artifactId)){
result.append('@').append(module.path);}if(!module.packaging.equals(module.modules.isEmpty()?"jar":"pom")){
result.append(' ').append(module.packaging);}if(module.aggregator!=null&&(module.parentKey==null||!module.parentKey.equals(module.aggregator.key()))){
Module parent=module.parentKey==null?null:modulesByKey.get(module.parentKey);result.append(" ^").append(parent==null?"":name(parent));
}List<String>dependencies=new ArrayList<String>();for(Dependency dependency:module.dependencies){Module target=modulesByKey.get(dependency.key);
if(target!=null&&target!=module){String rendered=name(target);if(dependency.type!=null&&!"jar".equals(dependency.type)){
rendered+="("+dependency.type+")";}if(dependency.scope!=null&&!"compile".equals(dependency.scope)){rendered+=":"+dependency.scope;
}dependencies.add(rendered);}}if(!dependencies.isEmpty()){result.append(" >");for(int i=0;i<dependencies.size();
i++){result.append(i>0?",":"").append(dependencies.get(i));}}result.append('\n');for(Module child:module.modules){
write(child,depth+1,result);}}private String name(Module module){String name=names.nameOf(module.key(),module.artifactId);
return name.equals(module.artifactId)?name:name.substring(0,name.length()-module.artifactId.length()-1)+":"+module.artifactId;
}private static Element parse(File pom){try{DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);DocumentBuilder builder=factory.newDocumentBuilder();
Document document=builder.parse(pom);return document.getDocumentElement();}catch(ParserConfigurationException|SAXException|IOException e){
throw new IllegalArgumentException("can't read "+pom,e);}}private static Element child(Element parent,String name){
List<Element>result=children(parent,name);return result.isEmpty()?null:result.get(0);}private static List<Element>children(Element parent,String name)
{List<Element>result=new ArrayList<Element>();for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling()){
if(node instanceof Element&&(name==null||name.equals(localName((Element)node)))){result.add((Element)node);
}}return result;}private static String localName(Element element){String tagName=element.getTagName();
return tagName.substring(tagName.indexOf(':')+1);}private static String text(Element parent,String name){
Element child=child(parent,name);return child==null?null:child.getTextContent().trim();}private static File normalize(File file){
try{return file.getCanonicalFile();}catch(IOException e){return file.getAbsoluteFile();}}private static final class Module{
final Module aggregator;final String path;final List<Module>modules=new ArrayList<Module>();final List<Dependency>dependencies=new ArrayList<
Dependency>();String groupId;String artifactId;String packaging;String parentKey;Map<String,String>properties=Collections.emptyMap();
Module(Module aggregator,String path){this.aggregator=aggregator;this.path=path;}String key(){return groupId+":"+artifactId;
}}private static final class Dependency{final String key;final String scope;final String type;Dependency(String key,String scope,String type){
this.key=key;this.scope=scope;this.type=type;}}public final static class MavenModuleGenerator{private static final List<String>SCOPES=Arrays.asList(
"compile","provided","runtime","test","system","import");private final List<ModuleLine>modules=new ArrayList<ModuleLine>();
private final Map<String,ModuleLine>byName=new HashMap<String,ModuleLine>();private String groupId="generated";
private String version="1.0.0-SNAPSHOT";private MavenModuleGenerator(String export){List<ModuleLine>stack=new ArrayList<ModuleLine>();
for(String line:export.split("\r?\n")){if(line.trim().isEmpty()){continue;}int depth=0;while(line.charAt(depth)==' '){
depth++;}ModuleLine module=new ModuleLine(line.trim(),depth==0?null:stack.get(depth-1));while(stack.size()>depth){
stack.remove(stack.size()-1);}stack.add(module);if(module.aggregator!=null){module.aggregator.children.add(module);
}modules.add(module);byName.put(module.name,module);}}public static MavenModuleGenerator of(String export){
return new MavenModuleGenerator(export);}public MavenModuleGenerator groupId(String value){groupId=value;
return this;}public MavenModuleGenerator version(String value){version=value;return this;}public static void main(String[]args)throws IOException{
if(args.length<2){System.err.println("usage: "+MavenModuleGenerator.class.getName()+
" <export file> <output directory> [--groupId=...] [--version=...]");return;}MavenModuleGenerator generator=of(new String(Files.readAllBytes(Paths.get
(args[0])),StandardCharsets.UTF_8));for(int i=2;i<args.length;i++){if(args[i].startsWith("--groupId=")){
generator.groupId(args[i].substring("--groupId=".length()));}else if(args[i].startsWith("--version=")){
generator.version(args[i].substring("--version=".length()));}}generator.writeTo(Paths.get(args[1]));
}public Map<String,String>generate(){Map<String,String>result=new TreeMap<String,String>();for(ModuleLine module:modules){
result.put(module.pomPath(),pom(module));}return result;}public void writeTo(Path directory)throws IOException{
for(Map.Entry<String,String>pom:generate().entrySet()){Path file=directory.resolve(pom.getKey());Files.createDirectories(file.getParent());
try(OutputStream out=Files.newOutputStream(file)){out.write(pom.getValue().getBytes(StandardCharsets.UTF_8));
}}}private String pom(ModuleLine module){StringBuilder result=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append(
"<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n").append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n").append(
"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n").append(
"    <modelVersion>4.0.0</modelVersion>\n\n");ModuleLine parent=module.parent();if(parent!=null){result.append("    <parent>\n").append(
"        <groupId>").append(groupIdOf(parent)).append("</groupId>\n").append("        <artifactId>").append(parent.artifactId()).append(
"</artifactId>\n").append("        <version>").append(version).append("</version>\n").append("        <relativePath>").append(relativePath(module,
parent)).append("</relativePath>\n").append("    </parent>\n\n");}if(parent==null||!groupIdOf(parent).equals(groupIdOf(module))){
result.append("    <groupId>").append(groupIdOf(module)).append("</groupId>\n");}result.append("    <artifactId>").append(module.artifactId()).append(
"</artifactId>\n");if(parent==null){result.append("    <version>").append(version).append("</version>\n");
}String packaging=module.packaging!=null?module.packaging:module.children.isEmpty()?null:"pom";if(packaging!=null){
result.append("    <packaging>").append(packaging).append("</packaging>\n");}if(!module.children.isEmpty()){
result.append("\n    <modules>\n");for(ModuleLine child:module.children){result.append("        <module>").append(child.pathInAggregator()).append(
"</module>\n");}result.append("    </modules>\n");}if(!module.dependencies.isEmpty()){result.append("\n    <dependencies>\n");
for(String dependency:module.dependencies){String name=dependency;String scope=null;String type=null;
int lastColon=name.lastIndexOf(':');if(lastColon>0&&SCOPES.contains(name.substring(lastColon+1))){scope=name.substring(lastColon+1);
name=name.substring(0,lastColon);}if(name.indexOf('(')>0){type=name.substring(name.indexOf('(')+1,name.indexOf(')'));
name=name.substring(0,name.indexOf('('));}ModuleLine target=byName.get(name);if(target==null){throw new IllegalArgumentException("unknown module "+
name+" (dependency of "+module.name+")");}result.append("        <dependency>\n").append("            <groupId>").append(groupIdOf(target)).append(
"</groupId>\n").append("            <artifactId>").append(target.artifactId()).append("</artifactId>\n").append(
"            <version>${project.version}</version>\n");if(type!=null){result.append("            <type>").append(type).append("</type>\n");
}if(scope!=null){result.append("            <scope>").append(scope).append("</scope>\n");}result.append("        </dependency>\n");
}result.append("    </dependencies>\n");}return result.append("</project>\n").toString();}private String groupIdOf(ModuleLine module){
return module.syntheticPackage()==null?groupId:groupId+"."+module.syntheticPackage();}private static String relativePath(ModuleLine module,ModuleLine
parent){Path root=Paths.get(File.separator+"project");Path moduleDirectory=root.resolve(module.pomPath()).getParent();
return moduleDirectory.relativize(root.resolve(parent.pomPath())).toString().replace(File.separatorChar,'/');
}private final class ModuleLine{final String name;final String path;String packaging;String parentName;
final List<String>dependencies=new ArrayList<String>();final ModuleLine aggregator;final List<ModuleLine>children=new ArrayList<ModuleLine>();
ModuleLine(String line,ModuleLine aggregator){this.aggregator=aggregator;String[]tokens=line.split(" ");
int at=tokens[0].indexOf('@');name=at<0?tokens[0]:tokens[0].substring(0,at);path=at<0?null:tokens[0].substring(at+1);
for(int i=1;i<tokens.length;i++){if(tokens[i].startsWith("^")){parentName=tokens[i].substring(1);}else if(tokens[i].startsWith(">")){
for(String dependency:tokens[i].substring(1).split(",")){dependencies.add(dependency);}}else{packaging=tokens[i];
}}}ModuleLine parent(){if(parentName==null){return aggregator;}if(parentName.isEmpty()){return null;
}ModuleLine parent=byName.get(parentName);if(parent==null){throw new IllegalArgumentException("unknown parent "+parentName+" of "+name);
}return parent;}String artifactId(){return name.substring(name.indexOf(':')+1);}String syntheticPackage(){
return name.indexOf(':')<0?null:name.substring(0,name.indexOf(':'));}String pathInAggregator(){return path!=null?path:artifactId();
}String pomPath(){if(aggregator==null){return"pom.xml";}String directory=aggregator.pomPath();directory=directory.contains("/")?directory.substring(0,
directory.lastIndexOf('/')+1):"";String relative=pathInAggregator();String result=directory+(relative.endsWith(".xml")?relative:relative+"/pom.xml");
return Paths.get(result).normalize().toString().replace(File.separatorChar,'/');}}}}

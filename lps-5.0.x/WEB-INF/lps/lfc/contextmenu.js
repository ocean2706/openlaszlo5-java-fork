/**
  * Definition of the context menu, from contextmenu.lzx
  *
  * @copyright Copyright 2009, 2010, 2011, 2012, 2013 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @access private
  *
  * @devnote See instructions in contextmenu.lzx on how to create this file
  */

#file contextmenu.lzx
#line 8
lz.ClassAttributeTypes["lzcontextmenuseparator"] = LzNode.mergeAttributeTypes(lz.ClassAttributeTypes["view"], {});
dynamic class $lzc$class_lzcontextmenuseparator extends LzView {
static var tagname = "lzcontextmenuseparator";static var __LZCSSTagSelectors = ["lzcontextmenuseparator", "view", "node", "$lfc$LzEventable", "Instance"];static var attributes = new LzInheritedHash(LzView.attributes);function $lg0t841 ($a) {
#file

#pragma "userFunctionName=handle oninit"
;
#file contextmenu.lzx
#line 10
this.parent.registerRedraw(this)
}
#file
function $lg0t842 ($a) {
#file

#pragma "userFunctionName=handle ondestroy"
;
#file contextmenu.lzx
#line 13
this.parent.unregisterRedraw(this)
}
#file
function redraw (context_$a) {
context_$a.moveTo(0, this.y + 9);
context_$a.lineTo(this.parent.width - 3, this.y + 9);
context_$a.strokeStyle = "#E5E5E5";
context_$a.stroke()
}
#file
function $lzc$class_lzcontextmenuseparator (parent_$a:LzNode? = null, attrs_$b:Object? = null, children_$c:Array? = null, async_$d:Boolean = false) {
super(parent_$a, attrs_$b, children_$c, async_$d)
}
LzNode.mergeAttributes({$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["lzcontextmenuseparator"]}, $delegates: ["oninit", "$lg0t841", null, "ondestroy", "$lg0t842", null], height: 10}, $lzc$class_lzcontextmenuseparator.attributes);
}


#file contextmenu.lzx
#line 23
lz.ClassAttributeTypes["lzcontextmenutext"] = LzNode.mergeAttributeTypes(lz.ClassAttributeTypes["text"], {});
dynamic class $lzc$class_lzcontextmenutext extends LzText {
static var tagname = "lzcontextmenutext";static var __LZCSSTagSelectors = ["lzcontextmenutext", "text", "view", "node", "$lfc$LzEventable", "Instance"];static var attributes = new LzInheritedHash(LzText.attributes);function $lg0t846 ($a) {
#file

#pragma "userFunctionName=handle onmouseover"
;
#file contextmenu.lzx
#line 25
this.parent.__overnow = this.data;
this.parent.registerRedraw(this)
}
#file
function $lg0t847 ($a) {
#file

#pragma "userFunctionName=handle onmouseout"
;
#file contextmenu.lzx
#line 29
this.parent.__overnow = null;
this.parent.unregisterRedraw(this)
}
#file
function $lg0t848 ($a) {
#file

#pragma "userFunctionName=handle onmouseup"
;
#file contextmenu.lzx
#line 33
this.parent.select(this.data);
this.parent.unregisterRedraw(this)
}
#file
function redraw (context_$a) {

context_$a.rect(this.x, this.y + 3, this.parent.width - 3, this.height);
context_$a.fillStyle = "#CCCCCC";
context_$a.fill()
}
#file
function $lzc$class_lzcontextmenutext (parent_$a:LzNode? = null, attrs_$b:Object? = null, children_$c:Array? = null, async_$d:Boolean = false) {
super(parent_$a, attrs_$b, children_$c, async_$d)
}
LzNode.mergeAttributes({$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["lzcontextmenutext"]}, $delegates: ["onmouseover", "$lg0t846", null, "onmouseout", "$lg0t847", null, "onmouseup", "$lg0t848", null], clickable: true, fgcolor: 0, fontsize: 11, fontstyle: "plain"}, $lzc$class_lzcontextmenutext.attributes);
}


#file contextmenu.lzx
#line 44
lz.ClassAttributeTypes["lzcontextmenudisabled"] = LzNode.mergeAttributeTypes(lz.ClassAttributeTypes["text"], {});
dynamic class $lzc$class_lzcontextmenudisabled extends LzText {
static var tagname = "lzcontextmenudisabled";static var __LZCSSTagSelectors = ["lzcontextmenudisabled", "text", "view", "node", "$lfc$LzEventable", "Instance"];static var attributes = new LzInheritedHash(LzText.attributes);function $lzc$class_lzcontextmenudisabled (parent_$a:LzNode? = null, attrs_$b:Object? = null, children_$c:Array? = null, async_$d:Boolean = false) {



super(parent_$a, attrs_$b, children_$c, async_$d)
}

LzNode.mergeAttributes({$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["lzcontextmenudisabled"]}, fgcolor: 13421772, fontsize: 11, fontstyle: "plain"}, $lzc$class_lzcontextmenudisabled.attributes);
}


#file contextmenu.lzx
#line 158
lz.ClassAttributeTypes["$lzc$class__lg0t84f"] = LzNode.mergeAttributeTypes(lz.ClassAttributeTypes["view"], {});
dynamic class $lzc$class__lg0t84f extends LzView {
static var displayName = "<anonymous extends='view'>";static var __LZCSSTagSelectors = ["view", "node", "$lfc$LzEventable", "Instance"];static var attributes = new LzInheritedHash(LzView.attributes);function $lg0t84a ($a) {
#file

#pragma "userFunctionName=width='${...}'"
;
#file contextmenu.lzx
#line 158
var $b = this.parent.container.width + 9;
#file
if ($b !== this["width"] || !this.inited) {
this.setAttribute("width", $b)
}}
#file contextmenu.lzx
#line 158
function $lg0t84b () {
#file

#pragma "userFunctionName=width dependencies"
;

#pragma "warnUndefinedReferences=false"
;

#pragma "throwsError=true"
;
#file contextmenu.lzx
#line 158
if ($debug) {
return $lzc$validateReferenceDependencies([this.parent.container, "width"], ["this.parent.container"])
} else {
return [this.parent.container, "width"]
}}
#line 158
function $lg0t84c ($a) {
#file

#pragma "userFunctionName=height='${...}'"
;
#file contextmenu.lzx
#line 158
var $b = this.parent.container.height + 9;
#file
if ($b !== this["height"] || !this.inited) {
this.setAttribute("height", $b)
}}
#file contextmenu.lzx
#line 158
function $lg0t84d () {
#file

#pragma "userFunctionName=height dependencies"
;

#pragma "warnUndefinedReferences=false"
;

#pragma "throwsError=true"
;
#file contextmenu.lzx
#line 158
if ($debug) {
return $lzc$validateReferenceDependencies([this.parent.container, "height"], ["this.parent.container"])
} else {
return [this.parent.container, "height"]
}}
#line 159
function $lg0t84e ($a) {
#file

#pragma "userFunctionName=handle oninit"
;
#file contextmenu.lzx
#line 160
this.createContext()
}









function __doredraw (ignore_$a = null) {

if (this.visible && this.width && this.height && this.context) this.redraw(this.context)
}


function redraw (context_$a = this.context) {
if (parent._lockdrawing) return;
context_$a.beginPath();
context_$a.clearRect(0, 0, this.width, this.height);

LzKernelUtils.rect(context_$a, 2.5, 3.5, this.width - 3, this.height - 3, this.classroot.inset);
context_$a.fillStyle = "#000000";
context_$a.globalAlpha = 0.2;
context_$a.fill();

context_$a.beginPath();
LzKernelUtils.rect(context_$a, 0, 0, this.width - 3, this.height - 3, this.classroot.inset);
context_$a.globalAlpha = 1;
context_$a.fillStyle = "#FFFFFF";
context_$a.fill();

context_$a.globalAlpha = 1;
context_$a.strokeStyle = "#CCCCCC";
context_$a.stroke();

for (var uid_$b in this.parent.__drawnitems) {
context_$a.beginPath();
this.parent.__drawnitems[uid_$b].redraw(context_$a)
}}
#file
var $classrootdepth;function $lzc$class__lg0t84f (parent_$a:LzNode? = null, attrs_$b:Object? = null, children_$c:Array? = null, async_$d:Boolean = false) {
super(parent_$a, attrs_$b, children_$c, async_$d)
}
LzNode.mergeAttributes({$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["$lzc$class__lg0t84f"]}}, $lzc$class__lg0t84f.attributes);
}


#file contextmenu.lzx
#line 46
lz.ClassAttributeTypes["lzcontextmenu"] = LzNode.mergeAttributeTypes(lz.ClassAttributeTypes["view"], {_lockdrawing: "boolean", defaultplacement: "string", inset: "number"});
dynamic class $lzc$class_lzcontextmenu extends LzView {
static var tagname = "lzcontextmenu";static var children = [{attrs: {$classrootdepth: 1, $delegates: ["oninit", "$lg0t84e", null, "onwidth", "__doredraw", null, "onheight", "__doredraw", null, "oncontext", "__doredraw", null, "onvisible", "__doredraw", null], height: new LzAlwaysExpr("$lg0t84c", "$lg0t84d", $debug ? "='${...}'" : null), name: "background", width: new LzAlwaysExpr("$lg0t84a", "$lg0t84b", $debug ? "='${...}'" : null)}, "class": $lzc$class__lg0t84f}, {attrs: {$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["container"]}, $classrootdepth: 1, name: "container", x: 3, y: 3}, "class": LzView}, {attrs: "container", "class": $lzc$class_userClassPlacement}];static var __LZCSSTagSelectors = ["lzcontextmenu", "view", "node", "$lfc$LzEventable", "Instance"];static var attributes = new LzInheritedHash(LzView.attributes);var inset;var __drawnitems;var __overnow;var _lockdrawing;function $lg0t849 ($a) {
#file

#pragma "userFunctionName=handle oninit"
;
#file contextmenu.lzx
#line 54
this.__globalmousedel = new (lz.Delegate)(this, "__handlemouse")
}
#file
function select (offset_$a) {
var cmenu_$b = LzMouseKernel.__showncontextmenu;
if (cmenu_$b) cmenu_$b.__select(offset_$a);


lz.GlobalMouse.__resetLastMouse();
this.hide()
}
#file
function show () {

if (this.visible && this.__overnow != null) {

this.select(this.__overnow);
this.__overnow = null;
return
};
this.updateMenuWidth();
this.__overnow = null;
var pos_$a = canvas.getMouse();
if (pos_$a.x > canvas.width - this.width) pos_$a.x = canvas.width - this.width;
if (pos_$a.y > canvas.height - this.height) pos_$a.y = canvas.height - this.height;
this.bringToFront();
this.setAttribute("x", pos_$a.x);
this.setAttribute("y", pos_$a.y);
this.setAttribute("visible", true);
this.__globalmousedel.register(lz.GlobalMouse, "onmouseup")
}


function updateMenuWidth () {
var maxWidth_$a = 0;
var subviews_$b = this.container.subviews;
for (var i_$c = 0;i_$c < subviews_$b.length;i_$c++) {
var subview_$d = subviews_$b[i_$c];
if (!subview_$d) continue;
maxWidth_$a = subview_$d.width > maxWidth_$a ? subview_$d.width : maxWidth_$a
};
for (var i_$c = 0;i_$c < subviews_$b.length;i_$c++) {
var subview_$d = subviews_$b[i_$c];
if (!subview_$d) continue;
if (subview_$d.width != maxWidth_$a) subview_$d.setAttribute("width", maxWidth_$a)
}}

function hide () {
this.__globalmousedel.unregisterAll();
var cmenu_$a = LzMouseKernel.__showncontextmenu;
if (cmenu_$a) cmenu_$a.__hide();
this.setAttribute("visible", false)
}
#file
function setItems (newitems_$a) {

this._lockdrawing = true;

this.__drawnitems = {};
var subviews_$b = this.container.subviews;

for (var i_$c = subviews_$b.length - 1;i_$c >= 0;i_$c--) {
var subview_$d = subviews_$b[i_$c];
if (!subview_$d) continue;
subview_$d.destroy()
};

var l_$e = newitems_$a.length;
var ypos_$f = 0;
for (var i_$c = 0;i_$c < l_$e;i_$c++) {
var item_$g = newitems_$a[i_$c];
var classref_$h = lz["lzcontextmenu" + item_$g.type];
if (classref_$h) {
var newview_$i = new classref_$h(this, {data: item_$g.offset, text: item_$g.label});
newview_$i.setAttribute("y", ypos_$f);
ypos_$f += newview_$i.height
}};

this.items = newitems_$a;
this._lockdrawing = false;
this.background.redraw()
}

function registerRedraw (who_$a) {
this.__drawnitems[who_$a.getUID()] = who_$a;
this.background.redraw()
}
#file
function unregisterRedraw (who_$a) {
delete this.__drawnitems[who_$a.getUID()];
this.background.redraw()
}
#file
function __handlemouse (view_$a) {
if (!view_$a) {
this.hide();
return
};
do {
if (view_$a is lz.lzcontextmenu) {
return
};
view_$a = view_$a.immediateparent
} while (view_$a !== canvas);

if (!LzMouseKernel.__maccontextshown) this.hide()
}
#file
var background;var container;function $lzc$class_lzcontextmenu (parent_$a:LzNode? = null, attrs_$b:Object? = null, children_$c:Array? = null, async_$d:Boolean = false) {
super(parent_$a, attrs_$b, children_$c, async_$d)
}
LzNode.mergeAttributes({$CSSDescriptor: {}, $attributeDescriptor: {types: lz.ClassAttributeTypes["lzcontextmenu"]}, $delegates: ["oninit", "$lg0t849", null], __drawnitems: {}, __overnow: null, _lockdrawing: false, clickable: true, inset: 5, options: {ignorelayout: true}, showhandcursor: false, visible: false}, $lzc$class_lzcontextmenu.attributes);
}


lz["lzcontextmenuseparator"] = $lzc$class_lzcontextmenuseparator;
lz["lzcontextmenutext"] = $lzc$class_lzcontextmenutext;
lz["lzcontextmenudisabled"] = $lzc$class_lzcontextmenudisabled;
lz["lzcontextmenu"] = $lzc$class_lzcontextmenu;
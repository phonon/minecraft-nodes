ace.define("ace/theme/nodes_dark",["require","exports","module","ace/lib/dom"], function(acequire, exports, module) {

exports.isDark = true;
exports.cssClass = "ace-nodes-dark";
exports.cssText =
`.ace-nodes-dark .ace_gutter {
background: #111;
color: #8F908A;
}
.ace-nodes-dark .ace_print-margin {
width: 1px;
background: #333;
}
.ace-nodes-dark {
background-color: #111;
color: #F8F8F2;
}
.ace-nodes-dark .ace_cursor {
color: #F8F8F0;
}
.ace-nodes-dark .ace_marker-layer .ace_selection {
background: #3A3A3A;
}
.ace-nodes-dark.ace_multiselect .ace_selection.ace_start {
box-shadow: 0 0 3px 0px #272822;
}
.ace-nodes-dark .ace_marker-layer .ace_step {
background: rgb(102, 82, 0);
}
.ace-nodes-dark .ace_marker-layer .ace_bracket {
margin: -1px 0 0 -1px;
border: 1px solid #1A1A1A;
}
.ace-nodes-dark .ace_marker-layer .ace_active-line {
background: #1A1A1A;
}
.ace-nodes-dark .ace_gutter-active-line {
background-color: #1A1A1A;
}
.ace-nodes-dark .ace_marker-layer .ace_selected-word {
border: 1px solid #49483E;
}
.ace-nodes-dark .ace_invisible {
color: #52524d;
}
.ace-nodes-dark .ace_entity.ace_name.ace_tag,
.ace-nodes-dark .ace_keyword,
.ace-nodes-dark .ace_meta.ace_tag,
.ace-nodes-dark .ace_storage {
color: #F92672;
}
.ace-nodes-dark .ace_punctuation,
.ace-nodes-dark .ace_punctuation.ace_tag {
color: #fff;
}
.ace-nodes-dark .ace_constant.ace_character,
.ace-nodes-dark .ace_constant.ace_language,
.ace-nodes-dark .ace_constant.ace_numeric,
.ace-nodes-dark .ace_constant.ace_other {
color: #fde240;
}
.ace-nodes-dark .ace_invalid {
color: #F8F8F0;
background-color: #F92672;
}
.ace-nodes-dark .ace_invalid.ace_deprecated {
color: #F8F8F0;
background-color: #AE81FF;
}
.ace-nodes-dark .ace_support.ace_constant,
.ace-nodes-dark .ace_support.ace_function {
color: #9B59B6;
}
.ace-nodes-dark .ace_fold {
background-color: #A6E22E;
border-color: #F8F8F2
}
.ace-nodes-dark .ace_storage.ace_type,
.ace-nodes-dark .ace_support.ace_class,
.ace-nodes-dark .ace_support.ace_type {
color: #66D9EF
}
.ace-nodes-dark .ace_entity.ace_name.ace_function,
.ace-nodes-dark .ace_entity.ace_other,
.ace-nodes-dark .ace_entity.ace_other.ace_attribute-name,
.ace-nodes-dark .ace_variable {
color: #FB786A;
}
.ace-nodes-dark .ace_variable.ace_parameter {
font-style: italic;
color: #FD971F;
}
.ace-nodes-dark .ace_string {
color: #A6E22E;
}
.ace-nodes-dark .ace_comment {
font-style: italic;
color: #75715E;
}
.ace-nodes-dark .ace_indent-guide {
background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAEklEQVQImWPQ0FD0ZXBzd/wPAAjVAoxeSgNeAAAAAElFTkSuQmCC) right repeat-y
};
`;
var dom = acequire("../lib/dom");
dom.importCssString(exports.cssText, exports.cssClass);
});

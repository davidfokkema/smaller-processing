<?php

$name = $_GET['name'];

if (is_null($name)) {
    header('Location: index.php');
    return;
}

//// get contents of pde file
$pde = file_get_contents('examples/'. $name .'/'. $name .'.pde');

//// parse first comment line as title of sketch
$title = "(Untitled)";
if (preg_match('/\/\/ *([^\n]*)/', $pde, $matches) != 0) {
    $title = $matches[1];
}

$PAGE_TITLE = $title .' &raquo; Examples &raquo; Processing Mobile';
require '../header.inc.php';
?>
<?php echo $title ?><br>
<br>
<applet code="com.barteo.emulator.applet.Main"
        archive="../me-applet.jar,../large.jar,examples/<?php echo $name ?>/midlet/<?php echo $name ?>.jar">
    <param name="midlet" value="shapeprimitives">
</applet>
<pre>
<?php
echo $pde;
?>
</pre>
<?php
 require '../footer.inc.php';
?>
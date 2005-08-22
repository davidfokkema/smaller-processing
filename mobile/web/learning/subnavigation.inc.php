<?php
$section = 0;
if (stristr($_SERVER['PHP_SELF'], SITE_ROOT .'learning/tutorials') 
    !== false) {
    $section = 1;
} else if (stristr($_SERVER['PHP_SELF'], SITE_ROOT .'learning/example.php') 
    !== false) {
    $section = 2;
}

?>

<div id="subnavigation" style="padding-left: 80px">
    <img src="<?php echo SITE_ROOT?>images/nav_bottomarrow.png" align="absmiddle">

<?php if ($section == 0) { ?>
    Examples
<?php } else { ?>
    <a href="<?php echo SITE_ROOT ?>learning/index.php">Examples</a>
<?php } ?>

    <span class="backslash">\</span>
<?php if ($section == 1) { ?>
    Tutorials
<?php } else { ?>
    <a href="<?php echo SITE_ROOT ?>learning/tutorials/index.php">Tutorials</a> 
<?php } ?>

</div>

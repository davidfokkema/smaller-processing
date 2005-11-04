<?php
require 'settings.inc.php';
?>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN"
                      "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<title><?php echo $PAGE_TITLE ?></title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
<script language="javascript">
<!--

function MM_openBrWindow(theURL,winName,features) { //v2.0
  window.open(theURL,winName,features);
}
//-->
</script>
<link rel="stylesheet" href="<?php echo SITE_ROOT?>css/mobile.css" type="text/css">
</head>
<body>
<div id="head"> 
    <img src="<?php echo SITE_ROOT?>images/mobile.png">
</div>
<div id="navigation"> 
    <img src="<?php echo SITE_ROOT?>images/nav_bottomarrow.png" align="absmiddle">

<?php if ($_SERVER['PHP_SELF'] == SITE_ROOT . 'index.php') { ?>
    Cover
<?php } else { ?>
    <a href="<?php echo SITE_ROOT ?>index.php">Cover</a>
<?php } ?>

    <span class="backslash">\</span>
<?php if (strstr($_SERVER['PHP_SELF'], SITE_ROOT . 'learning/') === false) { ?>
    <a href="<?php echo SITE_ROOT ?>learning/index.php">Learning</a>
<?php } else { ?>
    Learning
<?php } ?>

    <span class="backslash">\</span>
<?php if (strstr($_SERVER['PHP_SELF'], SITE_ROOT . 'reference/') === false) { ?>
    <a href="<?php echo SITE_ROOT ?>reference/index.php">Reference</a> 
<?php } else { ?>
    Reference
<?php } ?>

    <span class="backslash">\</span>
<?php if (strstr($_SERVER['PHP_SELF'], SITE_ROOT . 'download/') === false) { ?>
    <a href="<?php echo SITE_ROOT ?>download/index.php">Download</a>
<?php } else { ?>
    Download
<?php } ?>

    <span class="backslash">\</span>
<?php if (strstr($_SERVER['PHP_SELF'], SITE_ROOT . 'faq/') === false) { ?>
    <a href="<?php echo SITE_ROOT ?>faq/index.php">FAQ</a>
<?php } else { ?>
    FAQ
<?php } ?>

    <span class="backslash">\</span>
<?php if (strstr($_SERVER['PHP_SELF'], SITE_ROOT . 'discourse/') === false) { ?>
    <a href="<?php echo SITE_ROOT ?>discourse/index.php">Discourse</a>
<?php } else { ?>
    Discourse
<?php } ?>
</div>
<?php 
if (!(@include 'subnavigation.inc.php')) {
    if (!(@include '../subnavigation.inc.php')) {
        @include '../../subnavigation.inc.php';
    }
}
?>
<?php if ($PAGE_SHOWBACKINDEX) { 
    if (is_null($PAGE_BACK_LINK)) {
        $PAGE_BACK_LINK = 'index.php';
    }
    if (is_null($PAGE_BACK_NAME)) {
        $PAGE_BACK_NAME = 'Index';
    }
?>
<div class="backnavigation">
  <table border="0" cellspacing="0" cellpadding="0">
    <tr>
      <td align="right" width="50">
        <a href="<?php echo $PAGE_BACK_LINK ?>">
          <img src="<?php echo SITE_ROOT?>images/back_off.png" border="0" align="middle">
        </a>
      </td>
      <td valign="middle">
        <a href="<?php echo $PAGE_BACK_LINK ?>">
          <?php echo $PAGE_BACK_NAME ?>
        </a>
      </td>
    </tr>
  </table>
</div>
<?php } ?>
<div class="content">

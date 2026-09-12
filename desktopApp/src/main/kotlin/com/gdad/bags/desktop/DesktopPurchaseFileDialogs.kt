package com.gdad.bags.desktop

import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal fun choosePurchaseWorkbook(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Upload GDAD BAGS purchase bill"
        fileFilter = FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx")
        isAcceptAllFileFilterUsed = false
    }
    return chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
        ?.selectedFile
        ?.toPath()
}

internal fun choosePurchaseTemplateDestination(defaultFileName: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save GDAD BAGS purchase template"
        fileFilter = FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx")
        isAcceptAllFileFilterUsed = false
        selectedFile = File(defaultFileName)
    }
    val selected = chooser.takeIf { it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION }
        ?.selectedFile
        ?: return null
    val file = if (selected.name.endsWith(".xlsx", ignoreCase = true)) {
        selected
    } else {
        File(selected.parentFile, "${selected.name}.xlsx")
    }
    return file.toPath()
}

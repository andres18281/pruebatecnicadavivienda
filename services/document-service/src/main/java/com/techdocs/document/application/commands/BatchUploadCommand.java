package com.techdocs.document.application.commands;

import java.util.List;

public record BatchUploadCommand(List<UploadCommand> files) {
}

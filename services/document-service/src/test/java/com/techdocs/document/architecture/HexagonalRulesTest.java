package com.techdocs.document.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.techdocs.document", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalRulesTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..",
                            "org.springframework..",
                            "co.elastic.clients..",
                            "io.minio.."
                    );

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..",
                            "org.springframework..",
                            "co.elastic.clients.."
                    );
}

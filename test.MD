# Последовательность команд для запуска автотестов

Ниже — все варианты запуска: от «просто запустить всё» до точечных сценариев. Предполагается, что проект — **Maven** (для Gradle добавлю отдельный блок в конце).

---

## 0. Предварительная проверка окружения

```bash
# Проверить, что Java и Maven установлены
java -version
mvn -version

# Требуется Java 17+ (в проекте заявлена Java 21)
# Если нужно — переключить JDK (пример для Linux/macOS через sdkman)
sdk use java 21.0.2-tem
```

**Ожидаемый результат:**
```
openjdk version "21.0.x" ...
Apache Maven 3.9.x ...
```

---

## 1. Полная сборка и запуск unit-тестов (самый частый сценарий)

```bash
# Перейти в корень проекта
cd /path/to/xmpp-client-java

# Чистая сборка + компиляция + запуск unit-тестов
mvn clean test
```

**Что произойдёт:**
- `clean` — удалит `target/`
- `test` — скомпилирует `src/main/java`, скомпилирует `src/test/java`, запустит все тесты, **кроме** помеченных `@Tag("integration")` (благодаря настройке Surefire `excludedGroups=integration`)

**Ожидаемый хвост вывода:**
```
[INFO] Tests run: 149, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 2. Запуск только конкретного тест-класса

```bash
# Один класс
mvn test -Dtest=XmlUtilTest

# Один класс с указанием полного имени пакета
mvn test -Dtest=ru.miacomsoft.xmpp.stanza.XmlUtilTest
```

---

## 3. Запуск конкретного тест-метода

```bash
# Один метод в классе
mvn test -Dtest=XmlUtilTest#escapeAmpAndBrackets

# Несколько методов (через +)
mvn test -Dtest=XmlUtilTest#escapeAmpAndBrackets+escapeQuotes

# Несколько классов (через запятую)
mvn test -Dtest=XmlUtilTest,SaslAuthenticatorTest,EventBusTest
```

---

## 4. Запуск по маске

```bash
# Все тесты, оканчивающиеся на Test
mvn test -Dtest=*Test

# Все тесты в пакете module
mvn test -Dtest=ru.miacomsoft.xmpp.module.*Test

# Все, кроме XmlUtilTest
mvn test -Dtest=!XmlUtilTest
```

---

## 5. Запуск интеграционных тестов

По умолчанию они **исключены** из `mvn test`. Запускаются явно:

```bash
# Только integration-тесты
mvn test -Dgroups=integration

# Все тесты, включая integration
mvn test -DexcludedGroups=

# Или через surefire с явным указанием
mvn test -Dsurefire.groups=integration
```

**Ожидаемый вывод для `-Dgroups=integration`:**
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 6. Тихий / подробный режим

```bash
# Тихий — только итог
mvn -q test

# Подробный — вывод каждой станзы, каждый шаг
mvn test -X

# Только ошибки
mvn -q test 2>&1 | grep -E "ERROR|FAIL"
```

---

## 7. Пропустить тесты (например, для быстрой проверки компиляции)

```bash
mvn clean compile -DskipTests
```

Или скомпилировать тесты, но не запускать:

```bash
mvn clean test-compile -DskipTests
```

---

## 8. Запуск с несколькими потоками (ускоряет)

Для JUnit 5 параллельный запуск настраивается в `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
junit.jupiter.execution.parallel.mode.classes.default = concurrent
```

После этого:

```bash
mvn test
```

Или через параметры Surefire:

```bash
mvn test -Dparallel=classes -DthreadCount=4
```

---

## 9. Отчёты

```bash
# Обычный прогон — отчёты в target/surefire-reports/
mvn test
ls target/surefire-reports/

# HTML-отчёт (требует плагин surefire-report)
mvn surefire-report:report
# Открыть: target/site/surefire-report.html

# JUnit XML для CI
ls target/surefire-reports/TEST-*.xml
```

---

## 10. Полный CI-цикл (clean + compile + test + package)

```bash
mvn clean verify
```

`verify` = `test` + проверки (например, Failsafe для IT). Если настроен Failsafe-плагин для `*IT.java` — integration-тесты запустятся отдельно:

```bash
# Только Failsafe (IT)
mvn verify -DskipUnitTests=true
```

---

## 11. Запуск из IDE

### IntelliJ IDEA
1. `File → Open` → выбрать `pom.xml` → `Open as Project`.
2. Дождаться индексации Maven.
3. Правый клик по `src/test/java` → **Run 'All Tests'**.
4. Или по конкретному классу → **Run 'XmlUtilTest'**.
5. Для integration: правый клик по `FakeServerRegistrationIT` → **Run**.

### Eclipse / VS Code
- Eclipse: `Run → Run As → JUnit Test`.
- VS Code (Extension Pack for Java): правый клик по тест-классу → **Run Test**.

---

## 12. Полная последовательность «с нуля» (рекомендуемая)

```bash
# 1. Перейти в проект
cd /path/to/xmpp-client-java

# 2. Убедиться, что JDK 17+
java -version

# 3. Очистить предыдущие сборки
mvn clean

# 4. Скачать зависимости (первый раз — долго)
mvn dependency:resolve

# 5. Скомпилировать продакшен-код
mvn compile

# 6. Скомпилировать тесты (без запуска)
mvn test-compile

# 7. Запустить unit-тесты
mvn test

# 8. (Опционально) Запустить integration-тесты
mvn test -Dgroups=integration

# 9. (Опционально) Построить отчёт
mvn surefire-report:report

# 10. (Опционально) Собрать jar
mvn package -DskipTests
```

**Ожидаемый финал:**
```
[INFO] BUILD SUCCESS
[INFO] Total time:  12.345 s
```

---

## 13. Если что-то упало

```bash
# Посмотреть, какой именно тест упал
grep -r "FAILED\|ERROR" target/surefire-reports/

# Открыть txt-отчёт конкретного класса
cat target/surefire-reports/ru.miacomsoft.xmpp.stanza.XmlUtilTest.txt

# Открыть XML (детально, включая stack trace)
cat target/surefire-reports/TEST-ru.miacomsoft.xmpp.stanza.XmlUtilTest.xml

# Перезапустить только упавшие тесты (JUnit 5 + Surefire)
mvn test -Dsurefire.rerunFailingTestsCount=2
```

---

## 14. Только мои тесты (по тегу)

Если хотите помечать свои тесты тегом, например `@Tag("my")`:

```java
@Tag("my")
class MyNewTest { ... }
```

Запуск:

```bash
# Только тег my
mvn test -Dgroups=my

# Все теги кроме my
mvn test -DexcludedGroups=my

# Несколько тегов
mvn test -Dgroups=my,integration
```

---

## 15. Gradle (если проект на Gradle)

`build.gradle`:

```groovy
plugins {
    id 'java'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

test {
    useJUnitPlatform()
    // исключить integration по умолчанию
    excludeTags 'integration'
    testLogging {
        events "passed", "failed", "skipped"
        exceptionFormat "full"
    }
}

tasks.register('integrationTest', Test) {
    useJUnitPlatform {
        includeTags 'integration'
    }
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}
```

Команды:

```bash
# Все unit-тесты
./gradlew test

# Только конкретный класс
./gradlew test --tests "ru.miacomsoft.xmpp.stanza.XmlUtilTest"

# Только конкретный метод
./gradlew test --tests "ru.miacomsoft.xmpp.stanza.XmlUtilTest.escapeAmpAndBrackets"

# Интеграционные
./gradlew integrationTest

# Всё
./gradlew check

# Отчёт
./gradlew test --info
# HTML: build/reports/tests/test/index.html
```

---

## 16. Шпаргалка одной строкой

```bash
# Самое частое:
mvn clean test                                      # все unit
mvn test -Dtest=XmlUtilTest                         # один класс
mvn test -Dtest=XmlUtilTest#escapeAmpAndBrackets    # один метод
mvn test -Dgroups=integration                       # integration
mvn clean verify                                    # полный цикл
mvn surefire-report:report                          # HTML-отчёт
```

---

## 17. Что считать успехом

| Команда | Ожидаемый результат |
|---|---|
| `mvn -version` | `Apache Maven 3.9.x`, Java 17+ |
| `mvn clean test` | `Tests run: 149, Failures: 0, Errors: 0` |
| `mvn test -Dgroups=integration` | `Tests run: 7, Failures: 0, Errors: 0` |
| `mvn clean verify` | `BUILD SUCCESS` |
| `ls target/surefire-reports/` | `TEST-*.xml` файлы для каждого класса |
| `mvn surefire-report:report` | `target/site/surefire-report.html` создан |

Если все команды из пункта 12 прошли — тесты работают, окружение настроено, можно коммитить.
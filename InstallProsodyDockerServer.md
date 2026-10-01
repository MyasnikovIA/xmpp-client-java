
# Полное руководство: XMPP-сервер Prosody в Docker для Gajim

**Проверено на:** Linux Mint 22 «Zena» (база Ubuntu 24.04 noble) · Docker 29.x · Gajim (Windows)  
**Домен:** `smwrap.ru` → A-запись указывает на белый IP `109.203.192.122`  
**LAN-IP сервера:** `192.168.15.32`

---

## Что получится в итоге

- XMPP-сервер Prosody работает в Docker-контейнере.
- Пользователи **самостоятельно регистрируются** прямо из Gajim (In-Band Registration).
- Проброшены порты: 5222 (c2s), 5269 (s2s), 5280 (HTTP/BOSH).
- Администратор может управлять сервером через `prosodyctl`.
- TLS — self-signed сертификат с SAN-записями (для внутреннего/личного использования).

---

## 1. Установка Docker и Docker Compose

На Linux Mint **не нужен официальный репозиторий Docker** — он ломается из-за кодового имени Mint (`zena` вместо `noble`). Проще и надёжнее поставить из репозиториев Ubuntu:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker
```

Проверка:

```bash
docker --version
docker compose version
```

Должно вывести: Docker 2x.x.x и Docker Compose v2.x.x.  
Если `docker compose` не работает, но есть `docker-compose` — это одно и то же, просто используйте его.

> ⚠️ **Про `usermod: UID '0' already exists`** — это безобидное предупреждение внутри образа Prosody, оно НЕ является ошибкой и не влияет на работу.

---

## 2. Структура каталогов

```bash
sudo mkdir -p /opt/prosody/{config,data,logs,certs}
sudo chown -R 1000:1000 /opt/prosody
```

По итогу:

```
/opt/prosody/
├── docker-compose.yml
├── config/
│   ├── prosody.cfg.lua
│   └── certs/
│       ├── smwrap.ru.key
│       └── smwrap.ru.crt
├── data/         (сюда Prosody пишет аккаунты и БД)
└── logs/         (логи)
```

---

## 3. `docker-compose.yml`

Создайте `/opt/prosody/docker-compose.yml`:

```bash
sudo nano /opt/prosody/docker-compose.yml
```

Содержимое:

```yaml
services:
  prosody:
    image: prosody/prosody:latest
    container_name: prosody
    restart: unless-stopped
    ports:
      - "5222:5222"   # c2s — Gajim
      - "5269:5269"   # s2s — федерация
      - "5280:5280"   # http — BOSH/WebSocket
      - "5281:5281"   # не нужен, если TLS на Apache
    volumes:
      - ./config:/etc/prosody
      - ./data:/var/lib/prosody
      - ./logs:/var/log/prosody
    environment:
      - LOCAL=smwrap.ru
      - DOMAIN=smwrap.ru
```

---

## 4. Конфигурация Prosody

Создайте `/opt/prosody/config/prosody.cfg.lua`:

```bash
sudo mkdir /opt/prosody/config
sudo nano /opt/prosody/config/prosody.cfg.lua
```

Содержимое:

```lua
-- ============================================================
-- Prosody XMPP — конфигурация для smwrap.ru
-- Docker: prosody/prosody:latest
-- Клиенты: Gajim (десктоп), Converse.js (Web)
-- ============================================================

-- Администраторы сервера (JID целиком)
admins = { "admin@smwrap.ru" }

-- ------------------------------------------------------------
-- Модули
-- ------------------------------------------------------------
modules_enabled = {
    -- Основное
    "roster";            -- список контактов
    "saslauth";          -- аутентификация
    "tls";               -- шифрование
    "dialback";          -- s2s-федерация
    "disco";             -- service discovery
    "carbons";           -- синхронизация сообщений между устройствами
    "pep";               -- personal eventing (аватары, статусы)
    "private";           -- приватные XML-хранилища
    "blocklist";         -- блокировка пользователей
    "vcard4";            -- vCard 4
    "vcard_legacy";      -- vCard 3 (совместимость)
    "version";           -- версия сервера
    "uptime";            -- аптайм
    "time";              -- время сервера
    "ping";              -- ping/pong keepalive
    "register";          -- In-Band Registration (регистрация из Gajim)

    -- Веб-клиент (Converse.js)
    "bosh";              -- BOSH (HTTP long-polling, fallback)
    "websocket";         -- WebSocket (основной транспорт для браузера)

    -- Администрирование
    "admin_adhoc";       -- команды админа через XMPP
    "mam";   -- Message Archive Management
    "offline";
    -- Отправка файлов: включать ТОЛЬКО вместе с Component "upload.smwrap.ru"
    -- (см. ниже). Если компонент закомментирован — модуль не нужен.
    -- "http_upload";
}

-- Модули, которые НЕ нужно включать (оставлено для справки)
modules_disabled = {
    -- "offline";        -- офлайн-сообщения (включить, если нужно)
    -- "smacks";         -- stream management (полезно для мобильных)
}

-- ------------------------------------------------------------
-- Регистрация пользователей
-- ------------------------------------------------------------
allow_registration = true
registration_watchers = { "admin@smwrap.ru" }
-- registration_invite_only = true  -- включить после первичной настройки

-- ------------------------------------------------------------
-- Шифрование
-- ------------------------------------------------------------
-- ВНИМАНИЕ: сейчас используется self-signed сертификат,
-- поэтому шифрование не обязательно. После перехода на
-- Let's Encrypt — закомментировать три строки ниже.
c2s_require_encryption = false
s2s_require_encryption = false
allow_unencrypted_plain_auth = true

-- ------------------------------------------------------------
-- Логирование
-- ------------------------------------------------------------
log = {
    info  = "/var/log/prosody/prosody.log";
    error = "/var/log/prosody/prosody.err";
    -- debug = "/var/log/prosody/prosody.debug";  -- включать только для отладки
}

-- ------------------------------------------------------------
-- Пути к данным
-- ------------------------------------------------------------
data_path = "/var/lib/prosody"

-- ------------------------------------------------------------
-- HTTP / Web (для веб-клиента и BOSH)
-- ------------------------------------------------------------
http_ports      = { 5280 }
http_interfaces = { "*" }

-- HTTPS-порт внутри Prosody НЕ поднимаем:
-- TLS-терминацию делает Apache/nginx (валидный сертификат),
-- либо браузер через самоподписанный (неудобно, ошибки в консоли).
-- Если понадобится прямой WSS напрямую от Prosody — раскомментировать:
-- https_ports      = { 5281 }
-- https_interfaces = { "*" }

-- Пути для BOSH и WebSocket.
-- Итоговые URL для веб-клиента (через Apache-прокси):
--   WSS:  wss://smwrap.ru/xmpp-websocket
--   BOSH: https://smwrap.ru/http-bind
-- Напрямую (без прокси, из локальной сети по HTTP):
--   WS:   ws://109.203.192.122:5280/xmpp-websocket
--   BOSH: http://109.203.192.122:5280/http-bind
http_paths = {
    bosh      = "/http-bind";
    websocket = "/xmpp-websocket";
}

-- CORS — обязательно для работы браузерного клиента с другого origin
cross_domain_bosh         = true
consider_bosh_secure      = true
consider_websocket_secure = true

http_headers = {
    ["Access-Control-Allow-Origin"]  = "*";
    ["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS";
    ["Access-Control-Allow-Headers"] = "Content-Type";
}

-- ------------------------------------------------------------
-- Виртуальный хост
-- ------------------------------------------------------------
VirtualHost "smwrap.ru"
    ssl = {
        key         = "/etc/prosody/certs/smwrap.ru.key";
        certificate = "/etc/prosody/certs/smwrap.ru.crt";
    }

    -- Разрешить регистрацию именно на этом хосте
    allow_registration = true

-- ------------------------------------------------------------
-- Компоненты (ОПЦИОНАЛЬНО — включать после стабильного запуска!)
-- ------------------------------------------------------------
-- Раскомментировать ТОЛЬКО если DNS-записи
-- conference.smwrap.ru и upload.smwrap.ru указывают на этот сервер.
-- После включения компонента http_upload — раскомментировать
-- также "http_upload"; в modules_enabled выше.

-- Component "conference.smwrap.ru" "muc"
--     modules_enabled = { "muc_mam" }
--     restrict_room_creation = false

-- Component "upload.smwrap.ru" "http_upload"
--     http_upload_file_size_limit = 10485760         -- 10 МБ
--     http_upload_expire_after    = 60 * 60 * 24 * 7 -- 7 дней
```

---

## 5. TLS-сертификат (self-signed с SAN)

```bash
sudo mkdir /opt/prosody/config/certs/
sudo openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout /opt/prosody/config/certs/smwrap.ru.key \
  -out /opt/prosody/config/certs/smwrap.ru.crt \
  -days 3650 \
  -subj "/CN=smwrap.ru" \
  -addext "subjectAltName=DNS:smwrap.ru,DNS:conference.smwrap.ru,DNS:upload.smwrap.ru,IP:109.203.192.122,IP:192.168.15.32"

sudo chown -R 1000:1000 /opt/prosody/config/certs
```

> Если в будущем захотите Let's Encrypt — `certbot certonly --standalone -d smwrap.ru` (порт 80 должен быть свободен и указывать на этот сервер), затем скопировать `fullchain.pem` и `privkey.pem` в `config/certs/smwrap.ru.{crt,key}` и перезапустить контейнер. После этого строки с `require_encryption = false` можно убрать.

---

## 6. Права доступа

Обязательно:

```bash
sudo chown -R 1000:1000 /opt/prosody
```

Без этого контейнер не сможет писать в `data/` и `logs/`.

---

## 7. Firewall (ufw)

```bash
sudo ufw allow 5222/tcp
sudo ufw allow 5269/tcp
sudo ufw allow 5280/tcp
sudo ufw allow 5281/tcp
sudo ufw reload
sudo ufw status
```

---

## 8. Проброс портов на роутере

На роутере (где белый IP `109.203.192.122`) настройте **port forwarding** на `192.168.15.32`:

| Внешний порт | Внутренний IP | Внутренний порт | Протокол |
|---|---|---|---|
| 5222 | 192.168.15.32 | 5222 | TCP |
| 5269 | 192.168.15.32 | 5269 | TCP |
| 5280 | 192.168.15.32 | 5280 | TCP (опционально) |

---

## 9. DNS

У регистратора домена `smwrap.ru` должны быть A-записи:

```
smwrap.ru.              A   109.203.192.122
conference.smwrap.ru.   A   109.203.192.122   (если будете использовать MUC)
upload.smwrap.ru.       A   109.203.192.122   (если будете использовать http_upload)
```

Проверка (с любого хоста):

```bash
nslookup smwrap.ru
```

Ожидаемый ответ: `Address: 109.203.192.122`.

Опционально — SRV-записи (для правильной федерации):

```
_xmpp-client._tcp.smwrap.ru.  3600 IN SRV 5 0 5222 smwrap.ru.
_xmpp-server._tcp.smwrap.ru.  3600 IN SRV 5 0 5269 smwrap.ru.
```

---

## 10. Запуск сервера

```bash
cd /opt/prosody
docker-compose up -d
```

Проверка, что контейнер стабилен:

```bash
docker ps
```

Должны увидеть `STATUS: Up ...` (НЕ `Restarting`).

Если статус `Restarting` — смотрим логи:

```bash
docker-compose logs --tail=200 prosody
```

И ищем Lua-ошибки (обычно — незакрытые скобки, несуществующий модуль, отсутствующий файл сертификата).

---

## 11. Создание администратора

Только после того, как контейнер стабильно `Up`:

```bash
docker exec -it prosody prosodyctl adduser admin@smwrap.ru
```

Ввести пароль дважды. Ошибок и предупреждений быть не должно.

Проверка:

```bash
docker exec -it prosody prosodyctl check
```

Утилита покажет статус конфига, DNS, сертификатов.

---

## 12. Проверка на сервере

```bash
# Слушают ли порты
ss -tlnp | grep -E '5222|5269|5280|5281'

# Список пользователей
docker exec -it prosody ls /var/lib/prosody/smwrap.ru/accounts/

# Регистрация тестового пользователя через CLI
docker exec -it prosody prosodyctl register testuser smwrap.ru test12345

# Удаление тестового
docker exec -it prosody prosodyctl deluser testuser@smwrap.ru
```

Если всё ОК — переходим к клиенту.

---

## 13. Настройка Gajim (Windows)

### 13.1. Установка

Скачать с https://gajim.org → Windows Installer → установить → запустить.

### 13.2. Регистрация НОВОГО аккаунта из клиента

1. В окне приветствия: **«Учётная запись» → «Добавить»**.
2. Выбрать вкладку **«Зарегистрировать новую учётную запись»**.
3. Заполнить:
   - **Сервер (XMPP-домен):** `smwrap.ru`
   - **Логин:** например `user1` (получится `user1@smwrap.ru`)
   - **Пароль / Повтор пароля**
4. Нажать **«Зарегистрироваться»**.
5. Появится предупреждение о сертификате (он self-signed) — выбрать **«Доверять этому сертификату навсегда»**.
6. Готово — аккаунт создан, Gajim сразу подключится.

### 13.3. Подключение к уже созданному аккаунту

1. **«Учётная запись» → «Добавить»**.
2. Вкладка **«Вход в существующую учётную запись»**.
3. **JID:** `admin@smwrap.ru` (целиком, с @)
4. **Пароль:** из шага 11.
5. Подключиться, довериться сертификату.

### 13.4. Если Gajim не может подключиться

С Windows-клиента проверьте:

```powershell
Test-NetConnection smwrap.ru -Port 5222
Test-NetConnection smwrap.ru -Port 5269
```

Оба должны вернуть `TcpTestSucceeded : True`. Если `False` — проблема в пробросе портов или брандмауэре.

---

## 14. Управление сервером (шпаргалка)

```bash
# Остановить
cd /opt/prosody && docker-compose down

# Запустить
cd /opt/prosody && docker-compose up -d

# Перезапустить (после правки конфига)
docker-compose restart prosody

# Логи в реальном времени
docker-compose logs -f prosody

# Последние 200 строк логов
docker-compose logs --tail=200 prosody

# Список пользователей
docker exec -it prosody ls /var/lib/prosody/smwrap.ru/accounts/

# Создать пользователя
docker exec -it prosody prosodyctl adduser user2@smwrap.ru

# Сменить пароль
docker exec -it prosody prosodyctl passwd user2@smwrap.ru

# Удалить пользователя
docker exec -it prosody prosodyctl deluser user2@smwrap.ru

# Проверка состояния сервера
docker exec -it prosody prosodyctl check

# Обновить образ Prosody
docker-compose pull && docker-compose up -d
```

---

## 15. Опционально: групповые чаты (MUC) и отправка файлов

Добавлять **только после того, как базовый сервер стабильно работает**.  
Сначала добавьте `"http_upload";` в `modules_enabled`, затем в конец `prosody.cfg.lua`:

```lua
Component "conference.smwrap.ru" "muc"
    modules_enabled = { "muc_mam" }
    restrict_room_creation = false

Component "upload.smwrap.ru" "http_upload"
    http_upload_file_size_limit = 10485760
    http_upload_expire_after = 60 * 60 * 24 * 7
```

Перезапуск:

```bash
docker-compose restart prosody
docker ps        # убедиться, что Up, а не Restarting
```

> ⚠️ Компоненты работают только если DNS-записи `conference.smwrap.ru` и `upload.smwrap.ru` резолвятся в IP сервера.

---

## 16. Безопасность (важно!)

Сейчас `allow_registration = true`, а сервер доступен из интернета по белому IP — **любой, кто знает домен, может создать себе аккаунт**. Варианты защиты:

### Вариант A. Регистрация только по приглашению

В `prosody.cfg.lua`:

```lua
registration_invite_only = true
```

Тогда для создания аккаунта нужен код-приглашение, который генерируется админом.

### Вариант B. Отключить регистрацию, когда все аккаунты созданы

```lua
allow_registration = false
```

Плюс убрать `"register";` из `modules_enabled`. После этого создавать аккаунты только через `prosodyctl adduser`.

### Вариант C. Ограничить регистрацию по IP

```lua
registration_ip_limit = { "192.168.15.0/24" }
```

### Общие рекомендации

- Не оставляйте `c2s_require_encryption = false` в проде — переходите на Let's Encrypt и включайте обязательное шифрование.
- Держите Docker и образ Prosody в актуальном состоянии: `docker compose pull && docker compose up -d`.
- Логи: `/opt/prosody/logs/prosody.log` — периодически просматривайте.

---

## 17. Решение проблем

| Симптом | Причина | Решение |
|---|---|---|
| `docker: unknown command: docker compose` | Не установлен плагин v2 | Использовать `docker-compose` или поставить `docker-compose-v2` |
| Репозиторий Docker 404 для `zena` | Mint использует своё кодовое имя | Не использовать официальный репо Docker, ставить из Ubuntu (`docker.io`) |
| `Container name "/prosody" is already in use` | Старый контейнер не удалён | `docker compose down && docker rm -f prosody` |
| Контейнер в цикле `Restarting` | Ошибка в конфиге Prosody или в компонентах MUC/upload | `docker compose logs --tail=200 prosody`, искать Lua-ошибку; временно убрать компоненты |
| `usermod: UID '0' already exists` | Косметика entrypoint образа Prosody | Игнорировать |
| `The host 'smwrap.ru' is not listed` | В конфиге прописан другой домен | Заменить все `example.com` на `smwrap.ru` в `prosody.cfg.lua` |
| Gajim: «Не удаётся подключиться» | Порт 5222 закрыт / не проброшен | `sudo ufw allow 5222/tcp`; проверить port forwarding на роутере; `Test-NetConnection` |
| Gajim: предупреждение о сертификате | Self-signed сертификат | «Доверять навсегда» в Gajim |
| Gajim не даёт зарегистрироваться | `allow_registration = false` или нет модуля `register` | Проверить `prosody.cfg.lua`, перезапустить |
| Пользователь создан, но войти не может | Создан для домена, которого нет в конфиге | Смотреть предупреждение `prosodyctl`, исправить конфиг |

---

## 18. Итоговая последовательность команд (копипаст)

```bash
# 1. Docker
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
newgrp docker

# 2. Каталоги
sudo mkdir -p /opt/prosody/{config,data,logs,certs}
sudo chown -R 1000:1000 /opt/prosody

# 3. docker-compose.yml — создать по разделу 3
sudo nano /opt/prosody/docker-compose.yml

# 4. prosody.cfg.lua — создать по разделу 4
sudo nano /opt/prosody/config/prosody.cfg.lua

# 5. Сертификат
sudo openssl req -x509 -newkey rsa:4096 -nodes \
  -keyout /opt/prosody/config/certs/smwrap.ru.key \
  -out /opt/prosody/config/certs/smwrap.ru.crt \
  -days 3650 -subj "/CN=smwrap.ru" \
  -addext "subjectAltName=DNS:smwrap.ru,DNS:conference.smwrap.ru,DNS:upload.smwrap.ru,IP:109.203.192.122,IP:192.168.15.32"
sudo chown -R 1000:1000 /opt/prosody

# 6. Firewall
sudo ufw allow 5222/tcp
sudo ufw allow 5269/tcp
sudo ufw allow 5280/tcp
sudo ufw allow 5281/tcp
sudo ufw reload

# 7. Запуск
cd /opt/prosody
docker-compose up -d
docker ps

# 8. Админ
docker exec -it prosody prosodyctl adduser admin@smwrap.ru
docker exec -it prosody prosodyctl check

# 9. Пользователеи
docker exec -it prosody prosodyctl adduser myasnikovia@smwrap.ru
docker exec -it prosody prosodyctl adduser mibile@smwrap.ru
docker exec -it prosody prosodyctl adduser sototelegram@smwrap.ru

```

Далее на роутере — проброс портов, у регистратора — A-записи, в Gajim — подключение к `smwrap.ru` и регистрация новых аккаунтов прямо из клиента.



Проверка работы Docker:
```
cd /opt/prosody
docker-compose down
docker-compose up -d
docker exec -it prosody prosodyctl check
```



---
# Клиент

https://gajim.org/download/
https://gajim.org/downloads/snap/win/


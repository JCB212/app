# Modulo de Fechamento Fiscal Automatico - InfoAtiva

Sistema desktop para fechamento fiscal automatico, integrando com ERP Firebird 2.5.9.
Gera SPED Fiscal, SPED Contribuicoes, SINTEGRA, PDFs de resumo e envia tudo por email ao contador.

---

## Indice

1. [Requisitos do Sistema](#1-requisitos-do-sistema)
2. [Instalacao Passo a Passo](#2-instalacao-passo-a-passo)
3. [Configuracao Inicial](#3-configuracao-inicial)
4. [Como Usar](#4-como-usar)
5. [Estrutura dos Arquivos Gerados](#5-estrutura-dos-arquivos-gerados)
6. [Telas do Sistema](#6-telas-do-sistema)
7. [Solucao de Problemas](#7-solucao-de-problemas)
8. [Informacoes Tecnicas](#8-informacoes-tecnicas)

---

## 1. Requisitos do Sistema

### Obrigatorios
- **Windows 7, 8, 10 ou 11** (64 bits)
- **Java 17 ou superior** instalado na maquina
  - Download: https://adoptium.net/ (escolha "Temurin 17 LTS" para Windows x64)
- **Firebird 2.5.9** instalado e rodando com o banco do ERP
- Acesso de rede ao servidor do Firebird (porta 3050 por padrao)

### Opcionais
- Acesso a internet para envio de email (porta SMTP 465 ou 587)
- Antivirus configurado para nao bloquear o FiscalModule.exe

---

## 2. Instalacao Passo a Passo

### Passo 1: Instalar o Java 17

1. Acesse https://adoptium.net/
2. Clique em **"Latest LTS Release"** (versao 17 ou superior)
3. Escolha **Windows x64** e baixe o instalador `.msi`
4. Execute o instalador e siga as instrucoes (marque "Set JAVA_HOME" e "Add to PATH")
5. Para verificar a instalacao, abra o **Prompt de Comando** e digite:
   ```
   java -version
   ```
   Deve aparecer algo como: `openjdk version "17.0.x"`

### Passo 2: Copiar os Arquivos do Modulo Fiscal

Crie uma pasta para o programa, por exemplo: `C:\FiscalModule\`

Copie os seguintes arquivos para essa pasta:
```
C:\FiscalModule\
    FiscalModule.exe       (executavel principal)
    FiscalModule.jar       (alternativa ao .exe)
    IniciarFiscal.bat      (script de inicializacao alternativo)
    conexao.ini            (configuracao do banco - OBRIGATORIO)
    xmlContador.ini        (configuracoes gerais - criado automaticamente)
```

> **IMPORTANTE**: O arquivo `conexao.ini` PRECISA estar na mesma pasta do executavel
> ou no caminho `C:\TSD\Host\conexao.ini` (padrao do ERP).

### Passo 3: Configurar o conexao.ini

Edite o arquivo `conexao.ini` com as informacoes do seu servidor Firebird:

```ini
[CONEXAO]
IP_SERVIDOR=127.0.0.1
PORTA=3050
BASEHOST=C:\TSD\Host\HOST.FDB
```

- **IP_SERVIDOR**: IP do servidor onde o Firebird esta instalado
  - `127.0.0.1` se for na mesma maquina
  - IP do servidor se for em rede (ex: `192.168.1.100`)
- **PORTA**: Porta do Firebird (padrao: `3050`)
- **BASEHOST**: Caminho completo do arquivo `.FDB` do banco de dados

### Passo 4: Primeira Execucao

1. Dê duplo clique no **FiscalModule.exe**
   - Se nao funcionar, use o **IniciarFiscal.bat**
   - Se ainda nao funcionar, abra o Prompt de Comando e execute:
     ```
     cd C:\FiscalModule
     java -jar FiscalModule.jar
     ```

2. Na primeira execucao, o sistema cria automaticamente o arquivo `xmlContador.ini`
   com as configuracoes padrao

3. Se a senha estiver habilitada, faca login com seu usuario do ERP
   - Fallback: usuario `admin`, senha definida no `xmlContador.ini`

---

## 3. Configuracao Inicial

### 3.1. Caminhos dos XMLs

Acesse **Configuracoes** no menu lateral e configure os caminhos:

| Campo | Descricao | Valor Padrao |
|-------|-----------|-------------|
| XML NFe | Pasta dos XMLs de NFe emitidas | `C:\TSD\Host\XML` |
| XML NFCe | Pasta dos XMLs de NFCe (cupons) | `C:\TSD\Host\XML_NFCe` |
| XML Compras | Pasta dos XMLs de fornecedores | `C:\TSD\Host\XML_Fornecedores` |

> Use o botao "..." para navegar ate a pasta correta.
> O sistema busca XMLs recursivamente em subpastas.

### 3.2. Configuracao de Email

Acesse **Config. Email** no menu lateral:

**Configuracao padrao (InfoAtiva):**
| Campo | Valor |
|-------|-------|
| Servidor SMTP | `mail.infoativa.com.br` |
| Porta | `465` |
| Email | `fiscal@infoativa.com.br` |
| Senha | `Info2024@#--` |
| SSL | Sim |

Clique em **"Testar Conexao"** para verificar se esta funcionando.

**Presets rapidos**: Use os botoes Gmail, Hotmail, Yahoo, Terra, UOL para
preencher automaticamente os dados do servidor.

### 3.3. Destinatarios (Contadores)

Acesse **Destinatarios** no menu lateral:

1. Digite o email do contador no campo de texto
2. Clique em **"Adicionar"**
3. Repita para cada email adicional
4. Clique em **"Salvar Destinatarios"**

**Email padrao**: `jean.carlos@infoativa.com.br`

> Voce pode adicionar varios emails. Todos receberao o fechamento fiscal.

### 3.4. Seguranca (Opcional)

Em **Configuracoes > Seguranca**:
- Marque "Exigir senha de acesso" para ativar o login
- O sistema busca usuarios na tabela USUARIO do Firebird
- Se o banco nao estiver acessivel, use: admin / (senha do INI)

---

## 4. Como Usar

### 4.1. Modo Automatico (Recomendado)

1. Abra o programa
2. Clique em **"Gerar e Enviar"** no menu lateral
3. Selecione o modo **"Automatico (Mes Anterior)"**
4. Marque **"Enviar por email apos processar"**
5. Clique em **"Processar"**
6. Acompanhe o progresso no log (tela preta)
7. Quando terminar, o ZIP sera enviado automaticamente ao contador

> **Exemplo**: Se hoje e 14/04/2026, o sistema processara automaticamente
> o periodo de 01/03/2026 a 31/03/2026.

### 4.2. Modo Manual

1. Selecione o modo **"Manual"**
2. Escolha o **Mes** e o **Ano** desejado
3. Clique em **"Processar"**

### 4.3. Modo Anual

1. Selecione o modo **"Anual"**
2. Informe o **Ano** desejado (ex: 2025)
3. Clique em **"Processar"**
4. O sistema gera um relatorio para cada mes do ano, com pastas separadas

### 4.4. Dashboard

Acesse **Dashboard** no menu lateral para visualizar:
- **Graficos de vendas** (NFCe e NFe) dos ultimos 6 meses
- **Grafico de impostos** (pizza) do mes anterior
- **Grafico de compras** (linha) dos ultimos 6 meses
- **Cards resumo** com quantidades e totais

> O dashboard carrega dados diretamente do Firebird em tempo real.

### 4.5. Tipos de Relatorio (Checkboxes)

Na tela "Gerar e Enviar", voce pode selecionar quais relatorios gerar:

| Checkbox | Descricao |
|----------|-----------|
| Vendas NFCe | XMLs de cupons fiscais eletronicos |
| Vendas NFe | XMLs de notas fiscais eletronicas |
| Notas de Compra | XMLs de fornecedores |
| SPED Fiscal | Arquivo TXT no formato SPED EFD ICMS/IPI |
| SPED Contribuicoes | Arquivo TXT no formato SPED EFD PIS/COFINS |
| SINTEGRA | Arquivo TXT no formato SINTEGRA |
| Sequencias | PDF com analise de furos na numeracao |
| CST/CFOP | PDF com vendas/compras agrupadas por CST e CFOP |
| Monofasicos | PDF com itens de tributacao monofasica |
| Devolucoes | PDF com notas de devolucao |

---

## 5. Estrutura dos Arquivos Gerados

Todos os arquivos sao salvos na pasta `XMLContabilidade` ao lado do executavel:

```
XMLContabilidade/
    03_2026/                          (pasta do periodo)
        NFe/                          XMLs de NFe autorizadas
        NFCe/                         XMLs de NFCe autorizadas
        Compras/                      XMLs de notas de fornecedores
        Cancelados/                   XMLs de documentos cancelados
        Inutilizados/                 XMLs de documentos inutilizados
        TXT/
            SPED_FISCAL_03_2026.txt       SPED Fiscal
            SPED_CONTRIBUICOES_03_2026.txt SPED Contribuicoes
            SINTEGRA_03_2026.txt           SINTEGRA
        PDF/
            Resumo_Vendas_03_2026.pdf      Resumo de vendas
            Resumo_Impostos_03_2026.pdf    Resumo de impostos
            Resumo_Compras_03_2026.pdf     Resumo de compras
            Sequencias_03_2026.pdf         Furos de numeracao
            CST_CFOP_03_2026.pdf           Detalhamento CST/CFOP
            Monofasicos_03_2026.pdf        Itens monofasicos
            Devolucoes_03_2026.pdf         Notas de devolucao
    Fechamento_03_2026.zip            Arquivo ZIP com tudo
```

O ZIP e enviado automaticamente por email ao contador.

---

## 6. Telas do Sistema

### Tela Principal (Inicio)
- Cards com periodo atual, servidor e destinatarios
- Botoes de acao rapida para processar ou gerar anual
- Instrucoes de uso

### Dashboard
- Grafico de barras: Vendas NFCe e NFe dos ultimos 6 meses
- Grafico de pizza: Distribuicao de impostos (ICMS, PIS, COFINS, IPI, ICMS ST)
- Grafico de linha: Compras de fornecedores dos ultimos 6 meses
- Cards de resumo com quantidades e totais

### Gerar e Enviar
- Selecao de modo (Automatico, Manual, Anual)
- Selecao de periodo (mes/ano)
- Checkboxes para tipos de documento e relatorio
- Barra de progresso e log em tempo real
- Envio automatico por email

### Destinatarios
- Lista de emails dos contadores
- Adicionar/remover emails
- Salvar configuracao

### Config. Email
- Dados do servidor SMTP
- Teste de conexao
- Presets rapidos (Gmail, Hotmail, Yahoo, etc.)

### Configuracoes
- Caminhos dos XMLs (com navegacao de pastas)
- Seguranca (senha de acesso)
- Informacoes do banco de dados

---

## 7. Solucao de Problemas

### "Java nao encontrado" ao abrir o programa
- Instale o Java 17: https://adoptium.net/
- Reinicie o computador apos a instalacao
- Verifique com `java -version` no Prompt de Comando

### "Erro de conexao com Firebird"
- Verifique se o Firebird 2.5.9 esta rodando (servico "Firebird Server")
- Verifique o IP e porta no `conexao.ini`
- Verifique se o caminho do `.FDB` esta correto
- Teste a conexao pelo botao no menu lateral

### "Nenhum XML encontrado"
- Verifique os caminhos em Configuracoes
- Os XMLs devem estar em formato `.xml`
- O sistema busca recursivamente em subpastas
- Verifique se o periodo selecionado esta correto

### "Erro ao enviar email"
- Teste a conexao em Config. Email
- Verifique se o antivirus nao esta bloqueando a porta 465
- Verifique se a senha do email esta correta
- Tente trocar a porta para 587 (STARTTLS)

### O antivirus bloqueia o .exe
- Adicione `FiscalModule.exe` como excecao no antivirus
- Ou use `IniciarFiscal.bat` ou `java -jar FiscalModule.jar` diretamente

### Logs do sistema
- O arquivo `sistema_fiscal.log` e gerado na mesma pasta do executavel
- Contem logs detalhados de todas as operacoes
- Util para suporte tecnico

---

## 8. Informacoes Tecnicas

### Stack
| Tecnologia | Versao | Uso |
|------------|--------|-----|
| Java | 17+ | Linguagem principal |
| JavaFX | 17.0.6 | Interface grafica (charts, CSS) |
| Jaybird | 4.0.10 | Driver JDBC para Firebird 2.5.9 |
| Jakarta Mail | 2.0.1 | Envio de email SMTP (SSL/TLS) |
| Apache PDFBox | 2.0.31 | Geracao de PDFs |
| Apache Commons Compress | 1.26.2 | Compressao ZIP |
| ini4j | 0.5.4 | Leitura de arquivos INI |
| SLF4J + Logback | 2.0.13 | Sistema de logs |

### Tabelas Firebird Utilizadas
| Tabela | Descricao |
|--------|-----------|
| NFCE | Cupons fiscais eletronicos (82 colunas) |
| NFCE_ITENS | Itens dos cupons (79 colunas) |
| NFE | Notas fiscais eletronicas (133 colunas) |
| NOTA_COMPRA | Cabecalho das notas de compra (50 colunas) |
| NOTA_COMPRA_DETALHE | Itens das notas de compra (131 colunas) |
| PARAMETROS | Configuracoes do ERP |
| USUARIO | Usuarios para login |

### Build (para desenvolvedores)
```bash
# Requisitos: Java 17+, Maven 3.8+
cd fiscal-module
mvn clean package -DskipTests

# JAR gerado em:
target/fiscal-module-1.0.0.jar

# Para gerar o .EXE (requer Launch4j):
java -jar launch4j.jar launch4j-config.xml
```

### Versao
- **v1.2.0** - Dashboard com graficos, 7 PDFs, login DB, .EXE
- Desenvolvido por InfoAtiva

---

**Suporte**: Em caso de duvidas, entre em contato com o suporte tecnico.

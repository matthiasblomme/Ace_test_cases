"""Generate one tiny ACE application per executable-comment variant.

Each app: HTTPInput (/<app lowercased>, JSON domain) -> Compute -> HTTPReply.
One variant per app so that a runtime parse failure isolates to that app.
Writes two copies under work/: ws/<App> (for ibmint package) and tk/<App>/<App>
(one workspace per app for mqsicreatebar, so a Toolkit error in one app cannot
poison the verdict of another). Round selection: `python gen.py [2|3]`; the
chosen round's app names go to work/apps.txt, which build.cmd and toolkit.cmd read.
"""
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(ROOT, "work")
SCHEMA = "com.mbl.test"
SCHEMA_DIR = SCHEMA.replace(".", "/")

PROJECT_TEMPLATE = open(os.path.join(ROOT, "project.template.xml"), encoding="ascii").read()
DESCRIPTOR = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<ns2:appDescriptor xmlns="http://com.ibm.etools.mft.descriptor.base" '
    'xmlns:ns2="http://com.ibm.etools.mft.descriptor.app"><references/></ns2:appDescriptor>'
)

MSGFLOW_TEMPLATE = open(os.path.join(ROOT, "msgflow.template.xml"), encoding="ascii").read()

HEADER = "BROKER SCHEMA " + SCHEMA + "\n\n"

def module(app, body, schema_level="", header=HEADER):
    return (
        header
        + schema_level
        + "CREATE COMPUTE MODULE " + app + "_Compute\n"
        + "    CREATE FUNCTION Main() RETURNS BOOLEAN\n"
        + "    BEGIN\n"
        + "        SET OutputRoot.HTTPResponseHeader.\"Content-Type\" = 'application/json';\n"
        + "        SET OutputRoot.JSON.Data.plain = 'executed';\n"
        + body
        + "        SET OutputRoot.JSON.Data.after = 'executed';\n"
        + "        RETURN TRUE;\n"
        + "    END;\n"
        + "END MODULE;\n"
    )

VARIANTS = {}

# A - block form, one-line and multi-line, with normal comments as controls
VARIANTS["ExecBlock"] = module("ExecBlock",
    "        /* SET OutputRoot.JSON.Data.normalBlockComment = 'executed'; */\n"
    "        -- SET OutputRoot.JSON.Data.normalLineComment = 'executed';\n"
    "        /*!{ SET OutputRoot.JSON.Data.execBlockOneLine = 'executed'; }!*/\n"
    "        /*!{\n"
    "            DECLARE hidden INTEGER 42;\n"
    "            SET OutputRoot.JSON.Data.execBlockMultiLine = hidden;\n"
    "        }!*/\n"
)

# B - line form, hypothesis: everything after --!{ to end of line is code
VARIANTS["ExecLineOpen"] = module("ExecLineOpen",
    "        --!{ SET OutputRoot.JSON.Data.execLineOpen = 'executed';\n"
)

# B2 - line form, hypothesis: needs an explicit }! closer
VARIANTS["ExecLineClosed"] = module("ExecLineClosed",
    "        --!{ SET OutputRoot.JSON.Data.execLineClosed = 'executed'; }!\n"
)

# C1 - the BROKER SCHEMA statement itself inside an exec comment on line 1
VARIANTS["ExecFirstLineSchema"] = module("ExecFirstLineSchema",
    "        SET OutputRoot.JSON.Data.firstLineSchema = 'executed';\n",
    header="/*!{ BROKER SCHEMA " + SCHEMA + " }!*/\n\n",
)

# C2 - a schema-level DECLARE inside an exec comment on line 1, before BROKER SCHEMA
VARIANTS["ExecFirstLineDeclare"] = module("ExecFirstLineDeclare",
    "        SET OutputRoot.JSON.Data.firstLineDeclare = FIRST_CONST;\n",
    header="/*!{ DECLARE FIRST_CONST CONSTANT CHARACTER 'executed'; }!*/\n" + HEADER,
)

# C3 - same schema-level DECLARE, but after BROKER SCHEMA
VARIANTS["ExecAfterSchemaDeclare"] = module("ExecAfterSchemaDeclare",
    "        SET OutputRoot.JSON.Data.afterSchemaDeclare = AFTER_CONST;\n",
    schema_level="/*!{ DECLARE AFTER_CONST CONSTANT CHARACTER 'executed'; }!*/\n\n",
)

# D - a whole schema-level function hidden from the Toolkit, called from Main
VARIANTS["ExecHiddenRoutine"] = module("ExecHiddenRoutine",
    "        SET OutputRoot.JSON.Data.hiddenRoutine = HiddenGreeting('runtime');\n",
    schema_level=(
        "/*!{\n"
        "CREATE FUNCTION HiddenGreeting(IN who CHARACTER) RETURNS CHARACTER\n"
        "BEGIN\n"
        "    RETURN 'hello ' || who;\n"
        "END;\n"
        "}!*/\n\n"
    ),
)

# E - garbage inside the markers: Toolkit should be silent, runtime should refuse
VARIANTS["ExecGarbage"] = module("ExecGarbage",
    "        /*!{ this is not esql at all }!*/\n"
)

# F - bonus: do block comments nest at runtime? (Toolkit lexer keeps a stack)
VARIANTS["NestedBlockComment"] = module("NestedBlockComment",
    "        /* outer /* inner */ SET OutputRoot.JSON.Data.afterInnerClose = 'executed'; */\n"
)

# G - exec comment inside an expression: lexical strip or statement-level construct?
VARIANTS["ExecInExpression"] = module("ExecInExpression",
    "        SET OutputRoot.JSON.Data.inExpression = 1 /*!{ + 41 }!*/;\n"
)


# ---- round 2: placement rule, line-form continuation, Toolkit canary ----
VARIANTS2 = {}
NOSCHEMA = set()

# line form: the statement simply continues on the next line (marker is stripped, rest is code)
VARIANTS2["ExecLineContinued"] = module("ExecLineContinued",
    "        --!{ SET OutputRoot.JSON.Data.lineContinued =\n"
    "            'executed';\n"
)

# exec BROKER SCHEMA on line 2, after a normal comment on line 1
VARIANTS2["ExecSchemaLine2"] = module("ExecSchemaLine2",
    "        SET OutputRoot.JSON.Data.schemaLine2 = 'executed';\n",
    header="-- a normal comment on line 1\n/*!{ BROKER SCHEMA " + SCHEMA + " }!*/\n\n",
)

# exec BROKER SCHEMA on line 2, after a blank line 1
VARIANTS2["ExecSchemaLine2Blank"] = module("ExecSchemaLine2Blank",
    "        SET OutputRoot.JSON.Data.schemaLine2Blank = 'executed';\n",
    header="\n/*!{ BROKER SCHEMA " + SCHEMA + " }!*/\n\n",
)

# no BROKER SCHEMA at all (default schema): exec DECLARE on line 1
VARIANTS2["ExecNoSchemaFirstLine"] = module("ExecNoSchemaFirstLine",
    "        SET OutputRoot.JSON.Data.noSchemaFirstLine = NOSCHEMA_CONST;\n",
    header="/*!{ DECLARE NOSCHEMA_CONST CONSTANT CHARACTER 'executed'; }!*/\n\n",
)
NOSCHEMA.add("ExecNoSchemaFirstLine")

# no BROKER SCHEMA: normal comment on line 1, exec DECLARE on line 2
VARIANTS2["ExecNoSchemaLine2"] = module("ExecNoSchemaLine2",
    "        SET OutputRoot.JSON.Data.noSchemaLine2 = NOSCHEMA2_CONST;\n",
    header="-- a normal comment on line 1\n/*!{ DECLARE NOSCHEMA2_CONST CONSTANT CHARACTER 'executed'; }!*/\n\n",
)
NOSCHEMA.add("ExecNoSchemaLine2")

# canary: the same garbage as ExecGarbage but NOT inside the markers - the Toolkit MUST reject this
VARIANTS2["ToolkitCanaryGarbage"] = module("ToolkitCanaryGarbage",
    "        this is not esql at all\n"
)


# ---- round 3: the --@!{ marker (Toolkit "autogenerated code" annotation) ----
VARIANTS3 = {}

# claim under test: --@!{ is a single-line executable comment like --!{
VARIANTS3["ExecAtLine"] = module("ExecAtLine",
    "        --@!{ SET OutputRoot.JSON.Data.atLine = 'executed';\n"
)

# the exact shape the Toolkit's DatabaseEvent generator emits: banner text after the marker
VARIANTS3["ExecAtBanner"] = module("ExecAtBanner",
    "        --@!{ ******************** \"ReadEvents\" autogenerated code (1) ********************\n"
    "        SET OutputRoot.JSON.Data.insideBanner = 'executed';\n"
    "        --@!} ******************** \"ReadEvents\" autogenerated code (1) ********************\n"
)

# the closer alone: plain comment, or does it execute a stray '}' ?
VARIANTS3["ExecAtCloser"] = module("ExecAtCloser",
    "        --@!} SET OutputRoot.JSON.Data.atCloser = 'executed';\n"
)


def write_app(base, app, esql, noschema=False):
    appdir = os.path.join(base, app)
    if os.path.isdir(appdir):
        shutil.rmtree(appdir)
    schema = "" if noschema else SCHEMA
    schema_dir = "" if noschema else SCHEMA_DIR
    srcdir = os.path.join(appdir, schema_dir) if schema_dir else appdir
    os.makedirs(srcdir)
    with open(os.path.join(appdir, ".project"), "w", encoding="ascii", newline="\n") as f:
        f.write(PROJECT_TEMPLATE.replace("@APP@", app))
    with open(os.path.join(appdir, "application.descriptor"), "w", encoding="ascii", newline="\n") as f:
        f.write(DESCRIPTOR)
    flow = (MSGFLOW_TEMPLATE.replace("@APP@", app).replace("@URL@", "/" + app.lower())
            .replace("@SCHEMA@", schema))
    flow = flow.replace("@SCHEMADIR@/", schema_dir + "/" if schema_dir else "").replace("@SCHEMADIR@", schema_dir)
    with open(os.path.join(srcdir, app + ".msgflow"), "w", encoding="ascii", newline="\n") as f:
        f.write(flow)
    with open(os.path.join(srcdir, app + "_Compute.esql"), "w", encoding="ascii", newline="\n") as f:
        f.write(esql)


def main():
    ws = os.path.join(WORK, "ws")
    tk = os.path.join(WORK, "tk")
    rounds = {"2": VARIANTS2, "3": VARIANTS3}
    variants = rounds.get(sys.argv[1], VARIANTS) if len(sys.argv) > 1 else VARIANTS
    for app, esql in variants.items():
        write_app(ws, app, esql, app in NOSCHEMA)
        write_app(os.path.join(tk, app), app, esql, app in NOSCHEMA)
    with open(os.path.join(WORK, "apps.txt"), "w", encoding="ascii", newline="\n") as f:
        f.write(" ".join(variants.keys()) + "\n")
    print("generated", len(variants), "apps:", " ".join(variants.keys()))


if __name__ == "__main__":
    main()

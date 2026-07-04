#!/usr/bin/env python3
"""Fix JS escaping issues in buildWebUI script section"""
import os

workspace = '/data/user/0/com.ai.assistance.operit/files/workspace/c4404422-f66a-4219-a496-fac91531c552'
ktfile = os.path.join(workspace, 'app/src/main/java/com/material/localshare/server/LocalShareServer.kt')

with open(ktfile, 'r') as f:
    content = f.read()

# Find buildWebUI
start = content.find('    private fun buildWebUI(): String {')
if start < 0:
    print('ERROR: buildWebUI not found')
    exit(1)

script_start = content.find('<script>', start)
script_end = content.find('</script>', script_start)

before = content[:script_start + 8]  # include <script>
after = content[script_end:]          # from </script>
script = content[script_start + 8:script_end]

print(f'Script length before: {len(script)}')

# Fixes for backslash-single-quote issues in Kotlin triple-quoted strings:
# In Kotlin """, backslash is LITERAL. So \\' in source = two backslashes + quote.
# In JavaScript single-quoted strings, \\' = escaped backslash (produces \) then ' ends the string.
# Fix: change \\' to ' (removing the problematic backslash entirely).

# Fix 1: onclick="load(\\'\\')"  -> onclick="load('')"
script = script.replace("load(\\\\'\\\\')", "load('')")

# Fix2: cur.replace(/'/g,"\\\\'") -> cur.replace(/'/g,"&#39;")  
script = script.replace('"\\\\\\\\\'")', '"&#39;")')

# Fix3: +\\'\\\\') -> +'&#39;')
script = script.replace("+\\\\'\\\\\\\\'", "+'&#39;'")

# Fix4: this.style.display=\\'none\\' -> use safer approach  
script = script.replace("this.style.display=\\\\'none\\\\'", "this.style.display='none'")

# Fix5: Remove any remaining \\' (double backslash before quote)  
# Only where it's inside a JS single-quoted string context
# The remaining \\' patterns in bidetcrumb: \\'\\' -> '' 
script = script.replace("\\\\'\\\\'", "''")

print(f'Script length after: {len(script)}')

new_content = before + script + after
with open(ktfile, 'w') as f:
    f.write(new_content)

print('Done fixing buildWebUI script')

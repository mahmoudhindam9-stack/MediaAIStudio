import os
import re

def remove_duplicates(path):
    with open(path, "r") as f:
        content = f.read()
    
    # We can just extract all strings and keep the last one of each name
    matches = re.finditer(r'<string name="([^"]+)">([^<]*)</string>', content)
    strings = {}
    for match in matches:
        strings[match.group(1)] = match.group(0)
    
    # Rebuild the file
    lines = content.split('\n')
    new_lines = []
    
    # Find all strings and strip them
    for line in lines:
        if '<string name=' not in line and not line.strip().startswith('<?xml') and not line.strip().startswith('<resources') and not line.strip().startswith('</resources'):
            if line.strip() != "":
                new_lines.append(line)
        elif line.strip().startswith('<?xml') or line.strip().startswith('<resources') or line.strip().startswith('</resources'):
            new_lines.append(line)
    
    # Put resources block
    res_index = new_lines.index('</resources>')
    for name, tag in strings.items():
        new_lines.insert(res_index, '    ' + tag)
        res_index += 1
        
    with open(path, "w") as f:
        f.write("\n".join(new_lines))

remove_duplicates("app/src/main/res/values/strings.xml")
remove_duplicates("app/src/main/res/values-ar/strings.xml")

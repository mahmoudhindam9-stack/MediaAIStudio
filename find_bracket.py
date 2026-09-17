import sys

def find_closing(filename, start_line):
    with open(filename, 'r') as f:
        lines = f.readlines()
        
    stack = 0
    in_block = False
    
    for i in range(start_line - 1, len(lines)):
        line = lines[i]
        for char in line:
            if char == '{':
                stack += 1
                in_block = True
            elif char == '}':
                stack -= 1
                if in_block and stack == 0:
                    print(f"Closes at line {i + 1}")
                    return

if __name__ == "__main__":
    find_closing(sys.argv[1], int(sys.argv[2]))

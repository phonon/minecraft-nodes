'''
Reads through the source commands files and autogenerates
commands documentation from comments
'''

import os

TOKEN_COMMAND = '@command'
TOKEN_SUBCOMMAND = '@subcommand'

def parse_documentation(file):

    # output string
    output = ''
    
    # current token type
    token_type = None

    # string buffers for parsing 
    buffer = ''
    
    reading_block = False

    reading_command = False

    # finish reading a command:
    # appends string to output
    def finish_read_token(out, buffer):
        nonlocal reading_command

        if reading_command:
            out += buffer
            out += "\n"
            reading_command = False
            buffer = ''
        
        return out

    for line in file:
        line = line.strip() 
        if line.startswith('/**'):
            reading_block = True
        
        if line.startswith('*/'):
            output = finish_read_token(output, buffer)
            reading_block = False
        
        if reading_block:
            parts = line.split()
            
            # check if line starts with token
            if len(parts) > 1:
                if parts[1] == TOKEN_COMMAND:
                    output = finish_read_token(output, buffer)
                    if len(parts) > 2:
                        buffer = f"- **{' '.join(parts[2:])}**:"
                        reading_command = True
                elif parts[1] == TOKEN_SUBCOMMAND:
                    output = finish_read_token(output, buffer)
                    if len(parts) > 2:
                        buffer = f"   - **{' '.join(parts[2:])}**:"
                        reading_command = True
                else:
                    if reading_command and len(parts) > 2:
                        buffer += (' ' + ' '.join(parts[1:]))
            else: # current text broken, end
                output = finish_read_token(output, buffer)

    return output

# get paths to markdown template and source file directory
run_dir = os.path.dirname(os.path.realpath(__file__))
src_dir = os.path.join(run_dir, 'nodes', 'src')
if os.path.exists(src_dir) == False:
    # try going 1 folder up
    src_dir = os.path.join(run_dir, '..', 'nodes', 'src')
    if os.path.exists(src_dir) == False:
        print('Failed to find /src folder')
        print('Run this from the git project root')

# get output path
out_file_path = os.path.join('docs', 'src', '2-commands.md')

# parse source and write into markdown
commands_template_path = os.path.join(run_dir, 'commands_template.md')
with open(commands_template_path, 'r') as file_in:
    template = file_in.read()

    path_commands = os.path.join(src_dir, 'main', 'kotlin', 'phonon', 'nodes', 'commands')
    
    # town commands
    path_commands_town = os.path.join(path_commands, 'TownCommand.kt')
    with open(path_commands_town, 'r') as f:
        text = parse_documentation(f)
        template = template.replace('{town_commands}', text)
    
    # nation commands
    path_commands_nation = os.path.join(path_commands, 'NationCommand.kt')
    with open(path_commands_nation, 'r') as f:
        text = parse_documentation(f)
        template = template.replace('{nation_commands}', text)
    
    # nodes commands
    path_commands_nodes = os.path.join(path_commands, 'NodesCommand.kt')
    with open(path_commands_nodes, 'r') as f:
        text = parse_documentation(f)
        template = template.replace('{nodes_commands}', text)

    # nodes admin commands
    path_commands_nodesadmin = os.path.join(path_commands, 'NodesAdminCommand.kt')
    with open(path_commands_nodesadmin, 'r') as f:
        text = parse_documentation(f)
        template = template.replace('{nodesadmin_commands}', text)
    
    # write output
    with open(out_file_path, 'w+') as file_out:
        file_out.write(template)
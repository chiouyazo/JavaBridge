using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using ModHost.Models;

namespace ModHost;

public class ModHostBridge
{
    private readonly StreamWriter _writer;
    private readonly StreamReader _reader;
    private readonly Dictionary<string, TaskCompletionSource<string>> _pendingRequests = new();
    
    private readonly Dictionary<string, Func<string, Task>> _commandCallbacks = new();


    public ModHostBridge(StreamWriter writer, StreamReader reader)
    {
        _writer = writer;
        _reader = reader;
        Task.Run(ListenForResponses);
    }

    private async Task ListenForResponses()
    {
        while (true)
        {
            string? line = await _reader.ReadLineAsync();
            if (line == null) break;

            string[] parts = line.Split(new[] { ':' }, 3);
            if (parts.Length < 3) continue;

            string id = parts[0];
            string eventType = parts[1];
            string payload = parts[2];

            if (_pendingRequests.TryGetValue(id, out TaskCompletionSource<string>? tcs))
            {
                tcs.SetResult(line);
                _pendingRequests.Remove(id);
            }
            else
            {
                // handle unsolicited events here
                HandleEvent(eventType, payload);
            }
        }
    }

    private void HandleEvent(string eventType, string payload)
    {
        if (eventType == "COMMAND_EXECUTED")
        {
            // Example payload: "command_with_arg|arg1=123,arg2=hello"
            string[] parts = payload.Split(':', 2);
            
            string commandName = parts[0];
            string commandPayload = parts.Length > 1 ? parts[1] : string.Empty;

            Dictionary<string, string> argsDict = commandPayload.Split(',', StringSplitOptions.RemoveEmptyEntries)
                .Select(pair => pair.Split('=', 2))
                .ToDictionary(kv => kv[0], kv => kv.Length > 1 ? kv[1] : "");
            
            if (_commandCallbacks.TryGetValue(commandName, out Func<string, Task>? callback))
            {
                _ = callback(commandPayload);
            }
        }
        else
        {
            Console.WriteLine($"Unhandled event: {eventType} - {payload}");
        }
    }

    public async Task<string> SendRequestAsync(string id, string eventType, string payload)
    {
        TaskCompletionSource<string> tcs = new TaskCompletionSource<string>();
        _pendingRequests[id] = tcs;

        await _writer.WriteLineAsync($"{id}:{eventType}:{payload}");
        await _writer.FlushAsync();

        return await tcs.Task;
    }

    public async Task RegisterCommandAsync(string commandName, Func<string, Task> onExecuted)
    {
        string id = Guid.NewGuid().ToString();
        
        string response = await SendRequestAsync(id, "REGISTER_COMMAND", commandName);
        
        if (response == $"{id}:COMMAND_REGISTERED:{commandName}")
        {
            Console.WriteLine($"Registered command {commandName} successfully from C#.");
            _commandCallbacks.Add(commandName, onExecuted);
        }
        else
        {
            throw new Exception($"Command registration failed: {response}");
        }
    }

    public async Task RegisterCommandAsync(string commandName, List<CommandArgument> arguments, Func<string, Task> onExecuted)
    {
        string id = Guid.NewGuid().ToString();
        
        string argsPayload = string.Join(",", arguments.Select(arg => 
            $"{arg.Name}:{arg.Type}|{(arg.IsOptional ? "optional" : "required")}"));
        
        string payload = $"{commandName}|{argsPayload}";
        
        string response = await SendRequestAsync(id, "REGISTER_COMMAND", payload);
        
        if (response == $"{id}:COMMAND_REGISTERED:{payload}")
        {
            Console.WriteLine($"Registered command '{commandName}' with args successfully from C#.");
            _commandCallbacks.Add(commandName, onExecuted);
        }
        else
        {
            throw new Exception($"Command with args registration failed: {response}");
        }
    }
}
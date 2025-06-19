using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Threading.Tasks;
using ModHost.Models;

namespace ModHost;

public class Program
{
	public static async Task Main(string[] args)
	{
		if (args.Length == 0)
		{
			Console.WriteLine("Port argument required");
			return;
		}

		int port = int.Parse(args[0]);
		using TcpClient client = new TcpClient("127.0.0.1", port);
		NetworkStream stream = client.GetStream();
		StreamReader reader = new StreamReader(stream);
		StreamWriter writer = new StreamWriter(stream) { AutoFlush = true };

		ModHostBridge bridge = new ModHostBridge(writer, reader);

		await writer.WriteLineAsync("HELLO:ModHost");
            
		await bridge.RegisterCommandAsync("csharp", async payload =>
		{
			Console.WriteLine($"C# command 'csharp' executed with payload: {payload}");
			
			await Task.CompletedTask;
		});

		await bridge.RegisterCommandAsync("testTest", new List<CommandArgument>()
			{
				new CommandArgument()
				{
					IsOptional = false,
					Name = "Tester",
					Type = "integer"
				},
				new CommandArgument()
				{
					IsOptional = true,
					Name = "SecondTester",
					Type = "integer"
				}
			},
			async payload =>
			{
				// Returned payload: Tester=1,SecondTester=1
				Console.WriteLine($"C# command 'meow' executed with payload: {payload}");

				await Task.CompletedTask;
			});
		
		Console.WriteLine("ModHost ready and connected.");

		await Task.Delay(-1);
	}
}
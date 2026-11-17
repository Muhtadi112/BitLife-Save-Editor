using System.Collections;
using System.Globalization;
using System.Formats.Nrbf;

if (args.Length != 2)
{
    Console.Error.WriteLine("Usage: NrbfProbe <old-save.data> <new-save.data>");
    return 2;
}

Probe(args[0], "OLD");
Probe(args[1], "NEW");
return 0;

static void Probe(string path, string label)
{
    Console.WriteLine($"===== {label}: {Path.GetFileName(path)} =====");
    Console.WriteLine($"Bytes: {new FileInfo(path).Length}");

    using var stream = File.OpenRead(path);
    var root = NrbfDecoder.DecodeClassRecord(stream);
    Console.WriteLine($"Root type: {TypeLabel(root)}");
    Console.WriteLine("Root members: " + string.Join(", ", root.MemberNames));

    var visited = new HashSet<SerializationRecordId>();
    var visitedCount = 0;
    var financeCount = 0;
    var loanCount = 0;

    Visit(root, "$root", 0);

    Console.WriteLine($"Visited class records: {visitedCount}");
    Console.WriteLine($"SimFinances records: {financeCount}");
    Console.WriteLine($"SimBankLoan records: {loanCount}");
    Console.WriteLine();

    void Visit(SerializationRecord record, string pathName, int depth)
    {
        if (depth > 80 || visitedCount >= 100_000 || !visited.Add(record.Id))
            return;

        if (record is not ClassRecord current)
            return;

        visitedCount++;
        var typeName = TypeLabel(current);
        var isFinance = typeName.Contains("SimFinances", StringComparison.OrdinalIgnoreCase);
        var isLoan = typeName.Contains("SimBankLoan", StringComparison.OrdinalIgnoreCase);

        if (isFinance)
        {
            financeCount++;
            Console.WriteLine($"[SimFinances] {pathName}");
            foreach (var member in current.MemberNames)
                PrintMember(current, member, "  ");
        }

        if (isLoan)
        {
            loanCount++;
            Console.WriteLine($"[SimBankLoan] {pathName}");
            foreach (var member in current.MemberNames)
                PrintMember(current, member, "  ");
        }

        foreach (var member in current.MemberNames)
        {
            object? value;
            try { value = current.GetRawValue(member); }
            catch { continue; }

            if (value is ClassRecord child)
            {
                Visit(child, pathName + "." + member, depth + 1);
                continue;
            }

            // SZArrayRecord<T> exposes a parameterless GetArray(). Read only arrays
            // of decoded SerializationRecord instances; primitive arrays are skipped.
            if (value is ArrayRecord)
            {
                var method = value.GetType().GetMethods()
                    .FirstOrDefault(m => m.Name == "GetArray" && m.GetParameters().Length == 0);
                if (method is null) continue;

                try
                {
                    if (method.Invoke(value, null) is IEnumerable items)
                    {
                        var index = 0;
                        foreach (var item in items)
                        {
                            if (item is SerializationRecord nested)
                                Visit(nested, $"{pathName}.{member}[{index}]", depth + 1);
                            index++;
                            if (index >= 20_000) break;
                        }
                    }
                }
                catch { /* Unsupported array shapes are reported by the decoder itself. */ }
            }
        }
    }

    static void PrintMember(ClassRecord record, string member, string indent)
    {
        object? value;
        try { value = record.GetRawValue(member); }
        catch (Exception ex)
        {
            Console.WriteLine($"{indent}{member}: <read-error {ex.GetType().Name}>");
            return;
        }

        if (value is ClassRecord child)
        {
            Console.WriteLine($"{indent}{member}: class {TypeLabel(child)} (id={child.Id})");
        }
        else if (value is ArrayRecord array)
        {
            Console.WriteLine($"{indent}{member}: array {array.GetType().Name}");
        }
        else
        {
            var type = value?.GetType().FullName ?? "null";
            var text = Convert.ToString(value, CultureInfo.InvariantCulture) ?? "null";
            Console.WriteLine($"{indent}{member}: {type} = {text}");
        }
    }

    static string TypeLabel(SerializationRecord record) =>
        record.TypeName.AssemblyQualifiedName ?? record.TypeName.ToString();
}
